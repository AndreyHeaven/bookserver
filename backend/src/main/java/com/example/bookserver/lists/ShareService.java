package com.example.bookserver.lists;

import com.example.bookserver.domain.BookList;
import com.example.bookserver.domain.BookListShare;
import com.example.bookserver.lists.dto.PublicBookListDto;
import com.example.bookserver.lists.dto.ShareLinkDto;
import com.example.bookserver.repo.BookListShareRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Base64;

@Service
public class ShareService {

    private static final int TOKEN_BYTES = 24;

    private final BookListShareRepository shareRepository;
    private final ListAccessGuard guard;
    private final BookListMapper mapper;
    private final QrCodeService qrCodeService;
    private final String publicBaseUrl;
    private final SecureRandom random = new SecureRandom();
    private final Base64.Encoder urlEncoder = Base64.getUrlEncoder().withoutPadding();

    public ShareService(BookListShareRepository shareRepository,
                        ListAccessGuard guard,
                        BookListMapper mapper,
                        QrCodeService qrCodeService,
                        @Value("${app.public.base-url:http://localhost:8080}") String publicBaseUrl) {
        this.shareRepository = shareRepository;
        this.guard = guard;
        this.mapper = mapper;
        this.qrCodeService = qrCodeService;
        this.publicBaseUrl = stripTrailingSlash(publicBaseUrl);
    }

    @Transactional
    public ShareLinkDto share(Long listId, boolean regenerate) {
        BookList list = guard.requireOwnedList(listId);
        BookListShare share = shareRepository.findByListId(list.getId()).orElse(null);
        if (share != null && !regenerate) {
            return toLinkDto(share.getShareToken());
        }
        if (share == null) {
            share = new BookListShare();
            share.setList(list);
        }
        share.setShareToken(generateToken());
        shareRepository.save(share);
        return toLinkDto(share.getShareToken());
    }

    @Transactional
    public void revoke(Long listId) {
        guard.requireOwnedList(listId);
        shareRepository.deleteByListId(listId);
    }

    @Transactional(readOnly = true)
    public PublicBookListDto viewPublic(String token) {
        return mapper.toPublicDto(requireSharedList(token));
    }

    @Transactional(readOnly = true)
    public byte[] qrPng(String token) {
        requireSharedList(token);
        return qrCodeService.pngForUrl(publicUrl(token));
    }

    public String publicUrl(String token) {
        return publicBaseUrl + "/public/lists/" + token;
    }

    private BookList requireSharedList(String token) {
        return shareRepository.findByShareToken(token)
                .map(BookListShare::getList)
                .orElseThrow(() -> new EntityNotFoundException("Shared list not found for token"));
    }

    private ShareLinkDto toLinkDto(String token) {
        String url = publicUrl(token);
        String qrBase64 = Base64.getEncoder().encodeToString(qrCodeService.pngForUrl(url));
        return new ShareLinkDto(token, url, qrBase64);
    }

    private String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        return urlEncoder.encodeToString(bytes);
    }

    private static String stripTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
