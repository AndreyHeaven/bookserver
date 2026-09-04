package com.example.bookserver.telegram;

import com.example.bookserver.books.BookDownloadService;
import com.example.bookserver.books.BookSearchService;
import com.example.bookserver.books.dto.BookCardDto;
import com.example.bookserver.books.dto.BookDetailsDto;
import com.example.bookserver.books.dto.BookFileDto;
import com.example.bookserver.books.dto.BookSearchRequest;
import com.example.bookserver.books.dto.BookSearchResponse;
import com.example.bookserver.history.BookViewHistoryService;
import com.example.bookserver.repo.UserRepository;
import com.github.benmanes.caffeine.cache.Cache;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;


@Service
public class TelegramBotService {

    private static final String CALLBACK_PREFIX = "tg:";
    private final UserRepository userRepository;
    private final BookSearchService bookSearchService;
    private final BookDownloadService bookDownloadService;
    private final BookViewHistoryService historyService;
    private final TelegramApiClient telegramApiClient;
    private final Cache<String, TelegramAction> actionCache;
    private final Cache<String, TelegramAction.Download> downloadCache;
    private final TelegramProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();

    public TelegramBotService(UserRepository userRepository,
                              BookSearchService bookSearchService,
                              BookDownloadService bookDownloadService,
                              BookViewHistoryService historyService,
                              TelegramApiClient telegramApiClient,
                              Cache<String, TelegramAction> telegramActionCache,
                              Cache<String, TelegramAction.Download> telegramDownloadCache,
                              TelegramProperties properties) {
        this.userRepository = userRepository;
        this.bookSearchService = bookSearchService;
        this.bookDownloadService = bookDownloadService;
        this.historyService = historyService;
        this.telegramApiClient = telegramApiClient;
        this.actionCache = telegramActionCache;
        this.downloadCache = telegramDownloadCache;
        this.properties = properties;
    }

    public void handle(TelegramUpdate update) {
        if (!properties.enabled()) {
            return;
        }
        if (update.message() != null) {
            handleMessage(update.message());
        }
        if (update.callbackQuery() != null) {
            handleCallback(update.callbackQuery());
        }
    }

    private void handleMessage(TelegramMessage message) {
        if (message.from() == null || message.chat() == null || message.text() == null || message.text().isBlank()) {
            return;
        }
        Long userId = userId(message.from().id());
        if (userId == null) {
            telegramApiClient.sendMessage(message.chat().id(), "Доступ запрещён.", List.of());
            return;
        }
        sendSearchPage(message.chat().id(), message.from().id(), message.text().trim(), 0);
    }

    private void handleCallback(TelegramCallbackQuery callback) {
        if (callback.from() == null || callback.message() == null || callback.message().chat() == null || callback.data() == null) {
            return;
        }
        telegramApiClient.answerCallbackQuery(callback.id());
        TelegramAction action = action(callback.data(), callback.from().id());
        if (action == null || userId(callback.from().id()) == null) {
            telegramApiClient.sendMessage(callback.message().chat().id(), "Ссылка истекла или доступ запрещён.", List.of());
            return;
        }
        switch (action) {
            case TelegramAction.SearchPage searchPage -> sendSearchPage(
                    callback.message().chat().id(), searchPage.telegramUid(), searchPage.query(), searchPage.page());
            case TelegramAction.Details details -> sendDetails(callback.message().chat().id(), userId(callback.from().id()), details);
            case TelegramAction.Download download -> sendDownload(callback.message().chat().id(), userId(callback.from().id()), download);
        }
    }

    @Transactional(readOnly = true)
    protected Long userId(long telegramUid) {
        return userRepository.findByTelegramUid(telegramUid)
                .filter(user -> user.isEnabled())
                .map(user -> user.getId())
                .orElse(null);
    }

    private void sendSearchPage(long chatId, long telegramUid, String query, int page) {
        BookSearchResponse results = bookSearchService.search(
                new BookSearchRequest(query, List.of(), null, null, List.of(), null, page, properties.pageSize(), null),
                false);
        List<List<TelegramApiClient.TelegramInlineButton>> keyboard = new ArrayList<>();
        for (BookCardDto book : results.content()) {
            keyboard.add(List.of(button(book.title(), new TelegramAction.Details(telegramUid, book.id()))));
        }
        List<TelegramApiClient.TelegramInlineButton> pagination = new ArrayList<>();
        if (results.page() > 0) {
            pagination.add(button("←", new TelegramAction.SearchPage(telegramUid, query, results.page() - 1)));
        }
        if (results.page() + 1 < results.totalPages()) {
            pagination.add(button("→", new TelegramAction.SearchPage(telegramUid, query, results.page() + 1)));
        }
        if (!pagination.isEmpty()) {
            keyboard.add(pagination);
        }
        String text = results.content().isEmpty()
                ? "Книги не найдены."
                : "Результаты: страница " + (results.page() + 1) + " из " + results.totalPages();
        telegramApiClient.sendMessage(chatId, text, keyboard);
    }

    private void sendDetails(long chatId, long userId, TelegramAction.Details action) {
        BookDetailsDto details = bookSearchService.getDetails(action.bookId());
        historyService.record(userId, action.bookId());
        List<List<TelegramApiClient.TelegramInlineButton>> keyboard = details.files().stream()
                .map(file -> List.of(button(file.format(), new TelegramAction.Download(action.telegramUid(), action.bookId(), file.id()))))
                .toList();
        telegramApiClient.sendMessage(chatId, detailsText(details), keyboard);
    }

    private void sendDownload(long chatId, long userId, TelegramAction.Download action) {
        bookDownloadService.prepare(action.bookId(), action.fileId());
        historyService.record(userId, action.bookId());
        String token = token();
        downloadCache.put(token, action);
        String url = properties.publicBaseUrl().replaceAll("/+$", "") + "/api/telegram/download/" + token;
        telegramApiClient.sendMessage(chatId, url, List.of());
    }

    public BookDownloadService.BookFileDownload download(String token) {
        TelegramAction.Download download = downloadCache.asMap().remove(token);
        return download == null ? null : bookDownloadService.prepare(download.bookId(), download.fileId());
    }

    private TelegramAction action(String callbackData, long telegramUid) {
        if (!callbackData.startsWith(CALLBACK_PREFIX)) {
            return null;
        }
        TelegramAction action = actionCache.getIfPresent(callbackData.substring(CALLBACK_PREFIX.length()));
        return action != null && action.telegramUid() == telegramUid ? action : null;
    }

    private TelegramApiClient.TelegramInlineButton button(String text, TelegramAction action) {
        return new TelegramApiClient.TelegramInlineButton(text, CALLBACK_PREFIX + store(action));
    }

    private String store(TelegramAction action) {
        String token = token();
        actionCache.put(token, action);
        return token;
    }

    private String token() {
        byte[] bytes = new byte[18];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String detailsText(BookDetailsDto details) {
        String authors = details.authors().stream().map(author -> author.fullName()).filter(java.util.Objects::nonNull).reduce((a, b) -> a + ", " + b).orElse("Автор не указан");
        return details.title() + "\n" + authors + (details.year() == null ? "" : "\n" + details.year());
    }
}
