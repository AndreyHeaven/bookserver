package com.example.bookserver.telegram;

import com.example.bookserver.books.BookDownloadService;
import com.example.bookserver.books.BookSearchService;
import com.example.bookserver.books.dto.BookCardDto;
import com.example.bookserver.books.dto.BookDetailsDto;
import com.example.bookserver.books.dto.BookSearchRequest;
import com.example.bookserver.books.dto.BookSearchResponse;
import com.example.bookserver.history.BookViewHistoryService;
import com.example.bookserver.repo.UserRepository;
import com.github.benmanes.caffeine.cache.Cache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;


@Service
public class TelegramBotService {

    private static final Logger log = LoggerFactory.getLogger(TelegramBotService.class);
    private static final String CALLBACK_PREFIX = "tg:";
    private static final String ACCESS_DENIED = "Доступ запрещён.";
    private static final String LINK_EXPIRED = "Ссылка истекла или доступ запрещён.";
    private final UserRepository userRepository;
    private final BookSearchService bookSearchService;
    private final BookDownloadService bookDownloadService;
    private final BookViewHistoryService historyService;
    private final TelegramClient telegramClient;
    private final Cache<String, TelegramAction> actionCache;
    private final Cache<String, TelegramAction.Download> downloadCache;
    private final TelegramProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();

    public TelegramBotService(UserRepository userRepository,
                              BookSearchService bookSearchService,
                              BookDownloadService bookDownloadService,
                              BookViewHistoryService historyService,
                              TelegramClient telegramClient,
                              Cache<String, TelegramAction> telegramActionCache,
                              Cache<String, TelegramAction.Download> telegramDownloadCache,
                              TelegramProperties properties) {
        this.userRepository = userRepository;
        this.bookSearchService = bookSearchService;
        this.bookDownloadService = bookDownloadService;
        this.historyService = historyService;
        this.telegramClient = telegramClient;
        this.actionCache = telegramActionCache;
        this.downloadCache = telegramDownloadCache;
        this.properties = properties;
    }

    public void handle(Update update) {
        if (!properties.enabled()) {
            return;
        }
        if (update.hasMessage()) {
            handleMessage(update.getMessage());
        }
        if (update.hasCallbackQuery()) {
            handleCallback(update.getCallbackQuery());
        }
    }

    private void handleMessage(Message message) {
        if (message.getFrom() == null || message.getChat() == null
                || message.getText() == null || message.getText().isBlank()) {
            return;
        }
        long telegramUid = message.getFrom().getId();
        if (userId(telegramUid) == null) {
            send(message.getChatId(), ACCESS_DENIED, List.of());
            return;
        }
        sendSearchPage(message.getChatId(), telegramUid, message.getText().trim(), 0);
    }

    private void handleCallback(CallbackQuery callback) {
        if (callback.getFrom() == null || callback.getMessage() == null || callback.getData() == null) {
            return;
        }
        answerCallbackQuery(callback.getId());
        long telegramUid = callback.getFrom().getId();
        long chatId = callback.getMessage().getChatId();
        TelegramAction action = action(callback.getData(), telegramUid);
        Long userId = userId(telegramUid);
        if (action == null || userId == null) {
            send(chatId, LINK_EXPIRED, List.of());
            return;
        }
        switch (action) {
            case TelegramAction.SearchPage searchPage ->
                    sendSearchPage(chatId, searchPage.telegramUid(), searchPage.query(), searchPage.page());
            case TelegramAction.Details details -> sendDetails(chatId, userId, details);
            case TelegramAction.Download download -> sendDownload(chatId, userId, download);
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
        List<InlineKeyboardRow> keyboard = new ArrayList<>();
        for (BookCardDto book : results.content()) {
            keyboard.add(new InlineKeyboardRow(
                    button(book.title(), new TelegramAction.Details(telegramUid, book.id()))));
        }
        InlineKeyboardRow pagination = new InlineKeyboardRow();
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
        send(chatId, text, keyboard);
    }

    private void sendDetails(long chatId, long userId, TelegramAction.Details action) {
        BookDetailsDto details = bookSearchService.getDetails(action.bookId());
        historyService.record(userId, action.bookId());
        List<InlineKeyboardRow> keyboard = details.files().stream()
                .map(file -> new InlineKeyboardRow(button(file.format(),
                        new TelegramAction.Download(action.telegramUid(), action.bookId(), file.id()))))
                .toList();
        send(chatId, detailsText(details), keyboard);
    }

    private void sendDownload(long chatId, long userId, TelegramAction.Download action) {
        bookDownloadService.prepare(action.bookId(), action.fileId());
        historyService.record(userId, action.bookId());
        String token = token();
        downloadCache.put(token, action);
        String url = properties.publicBaseUrl().replaceAll("/+$", "") + "/api/telegram/download/" + token;
        send(chatId, url, List.of());
    }

    public BookDownloadService.BookFileDownload download(String token) {
        TelegramAction.Download download = downloadCache.asMap().remove(token);
        return download == null ? null : bookDownloadService.prepare(download.bookId(), download.fileId());
    }

    /**
     * Sends a message, keeping a Telegram-side failure (network, rate limit, blocked bot)
     * from aborting the update being processed — the next update must still be served.
     */
    private void send(long chatId, String text, List<InlineKeyboardRow> keyboard) {
        SendMessage message = SendMessage.builder()
                .chatId(chatId)
                .text(text)
                .replyMarkup(InlineKeyboardMarkup.builder().keyboard(keyboard).build())
                .build();
        try {
            telegramClient.execute(message);
        } catch (TelegramApiException exception) {
            log.warn("Failed to send Telegram message to chat {}", chatId, exception);
        }
    }

    private void answerCallbackQuery(String callbackQueryId) {
        try {
            telegramClient.execute(AnswerCallbackQuery.builder().callbackQueryId(callbackQueryId).build());
        } catch (TelegramApiException exception) {
            log.warn("Failed to answer Telegram callback query {}", callbackQueryId, exception);
        }
    }

    private TelegramAction action(String callbackData, long telegramUid) {
        if (!callbackData.startsWith(CALLBACK_PREFIX)) {
            return null;
        }
        TelegramAction action = actionCache.getIfPresent(callbackData.substring(CALLBACK_PREFIX.length()));
        return action != null && action.telegramUid() == telegramUid ? action : null;
    }

    private InlineKeyboardButton button(String text, TelegramAction action) {
        return InlineKeyboardButton.builder()
                .text(text)
                .callbackData(CALLBACK_PREFIX + store(action))
                .build();
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
        String authors = details.authors().stream()
                .map(author -> author.fullName())
                .filter(Objects::nonNull)
                .reduce((a, b) -> a + ", " + b)
                .orElse("Автор не указан");
        return details.title() + "\n" + authors + (details.year() == null ? "" : "\n" + details.year());
    }
}
