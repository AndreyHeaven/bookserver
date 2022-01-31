package ru.andreyheaven.bookserver.controler;

import com.rometools.rome.feed.atom.*;
import com.rometools.rome.io.*;
import org.springframework.web.bind.annotation.*;
import ru.andreyheaven.bookserver.service.*;

@RestController
@RequestMapping("/opds")
public class OPDSController {
    private final OPDSService service;

    public OPDSController(OPDSService service) {
        this.service = service;
    }


    @GetMapping(value = "/")
    public Feed index() {
        return service.createOpds();
    }

    @GetMapping(value = "/authorsindex/{author}")
    public Feed authorsindex(@PathVariable(name = "author", required = false) String author) {
        return service.createAuthorIndex(author);
    }

    /**
     * <?xml version="1.0" encoding="utf-8"?>
     * <feed xmlns="http://www.w3.org/2005/Atom">
     *     <id>tag:root:authors:3</id>
     *     <title>Книги по авторам</title>
     *     <updated>2022-01-27T12:46:20+01:00</updated>
     *     <icon>/favicon.ico</icon>
     *     <link href="/opds-opensearch.xml" rel="search" type="application/opensearchdescription+xml"/>
     *     <link href="/opds/search?searchTerm={searchTerms}" rel="search" type="application/atom+xml"/>
     *     <link href="/opds" rel="start" type="application/atom+xml;profile=opds-catalog"/>
     *     <entry>
     *         <updated>2022-01-27T12:46:20+01:00</updated>
     *         <id>tag:author:189603</id>
     *         <title>Хёрст III Дж. Кэмерон</title>
     *         <content type="text">1 книга</content>
     *         <link href="/opds/author/189603" type="application/atom+xml;profile=opds-catalog"/>
     *         <link href="/opds/authorsequences/189603" rel="http://www.feedbooks.com/opds/facet"
     *               type="application/atom+xml;profile=opds-catalog" title="Книги автора по сериям"/>
     *         <link href="/opds/authorsequenceless/189603" rel="http://www.feedbooks.com/opds/facet"
     *               type="application/atom+xml;profile=opds-catalog" title="Книги автора вне серий"/>
     *     </entry>
     *     <entry>
     *         <updated>2022-01-27T12:46:20+01:00</updated>
     *         <id>tag:author:33443</id>
     *         <title>37signals</title>
     *         <content type="text">1 книга</content>
     *         <link href="/opds/author/33443" type="application/atom+xml;profile=opds-catalog"/>
     *         <link href="/opds/authorsequences/33443" rel="http://www.feedbooks.com/opds/facet"
     *               type="application/atom+xml;profile=opds-catalog" title="Книги автора по сериям"/>
     *         <link href="/opds/authorsequenceless/33443" rel="http://www.feedbooks.com/opds/facet"
     *               type="application/atom+xml;profile=opds-catalog" title="Книги автора вне серий"/>
     *     </entry>
     *     <entry>
     *         <updated>2022-01-27T12:46:20+01:00</updated>
     *         <id>tag:author:50794</id>
     *         <title>Залесский Константин Александрович</title>
     *         <content type="text">22 книги</content>
     *         <link href="/opds/author/50794" type="application/atom+xml;profile=opds-catalog"/>
     *         <link href="/ia/63/109263/zalesskii.jpg" rel="http://opds-spec.org/image" type="image/jpeg"/>
     *         <link href="/ia/63/109263/zalesskii.jpg" rel="x-stanza-cover-image" type="image/jpeg"/>
     *         <link href="/ia/63/109263/zalesskii.jpg" rel="http://opds-spec.org/image/thumbnail" type="image/jpeg"/>
     *         <link href="/ia/63/109263/zalesskii.jpg" rel="x-stanza-cover-image-thumbnail" type="image/jpeg"/>
     *         <link href="/opds/authorsequences/50794" rel="http://www.feedbooks.com/opds/facet"
     *               type="application/atom+xml;profile=opds-catalog" title="Книги автора по сериям"/>
     *         <link href="/opds/authorsequenceless/50794" rel="http://www.feedbooks.com/opds/facet"
     *               type="application/atom+xml;profile=opds-catalog" title="Книги автора вне серий"/>
     *     </entry>
     * </feed>
     * @param author
     * @return
     * @throws FeedException
     */
    @GetMapping(value = "/authors/{author}")
    public Feed authors(@PathVariable(name = "author") String author) throws FeedException {
        return service.getAuthors(author);
    }

    /**
     * <?xml version="1.0" encoding="utf-8"?>
     * <feed xmlns="http://www.w3.org/2005/Atom">
     *     <id>tag:author:189603</id>
     *     <title>Книги автора Хёрст III Дж. Кэмерон</title>
     *     <updated>2022-01-27T12:47:38+01:00</updated>
     *     <icon>/favicon.ico</icon>
     *     <link href="/opds-opensearch.xml" rel="search" type="application/opensearchdescription+xml"/>
     *     <link href="/opds/search?searchTerm={searchTerms}" rel="search" type="application/atom+xml"/>
     *     <link href="/opds" rel="start" type="application/atom+xml;profile=opds-catalog"/>
     *     <entry>
     *         <updated>2022-01-27T12:47:38+01:00</updated>
     *         <id>tag:author:bio:189603</id>
     *         <title>Об авторе</title>
     *         <content type="text/html">&lt;p&gt;Дж. Кэмерон Хёрст III является профессором истории и восточноазиатских языков
     *             и культуры, а также директором Центра Восточноазиатских Исследований в Университете штата Канзас.&lt;/p&gt;
     *         </content>
     *         <link href="/a/189603" rel="alternate" type="text/html" title="Страница автора на сайте"/>
     *         <link href="/a/189603" rel="http://opds-spec.org/acquisition" type="text/html"
     *               title="Страница автора на сайте"/>
     *         <link href="/opds/authorsequences/189603" rel="http://www.feedbooks.com/opds/facet"
     *               type="application/atom+xml;profile=opds-catalog" title="Книги автора по сериям"/>
     *         <link href="/opds/authorsequenceless/189603" rel="http://www.feedbooks.com/opds/facet"
     *               type="application/atom+xml;profile=opds-catalog" title="Книги автора вне серий"/>
     *     </entry>
     *     <entry>
     *         <updated>2022-01-27T12:47:38+01:00</updated>
     *         <id>tag:author:189603:sequences</id>
     *         <title>Книги по сериям</title>
     *         <link href="/opds/authorsequences/189603" type="application/atom+xml;profile=opds-catalog"/>
     *     </entry>
     *     <entry>
     *         <updated>2022-01-27T12:47:38+01:00</updated>
     *         <id>tag:author:189603:sequenceless</id>
     *         <title>Книги вне серий</title>
     *         <link href="/opds/author/189603/authorsequenceless" type="application/atom+xml;profile=opds-catalog"/>
     *     </entry>
     *     <entry>
     *         <updated>2022-01-27T12:47:38+01:00</updated>
     *         <id>tag:author:189603:alphabet</id>
     *         <title>Книги по алфавиту</title>
     *         <link href="/opds/author/189603/alphabet" type="application/atom+xml;profile=opds-catalog"/>
     *     </entry>
     *     <entry>
     *         <updated>2022-01-27T12:47:38+01:00</updated>
     *         <id>tag:author:189603:time</id>
     *         <title>Книги по дате поступления</title>
     *         <link href="/opds/author/189603/time" type="application/atom+xml;profile=opds-catalog"/>
     *     </entry>
     * </feed>
     * @param id
     * @return
     * @throws FeedException
     */
    @GetMapping(value = "/author/{id}")
    public Feed author(@PathVariable(name = "id") Integer id){
        return service.getAuthor(id);
    }


    /**
     * <?xml version="1.0" encoding="utf-8"?>
     * <feed xmlns="http://www.w3.org/2005/Atom" xmlns:dc="http://purl.org/dc/terms/">
     *     <id>tag:author:189603:books:alphabet:</id>
     *     <title>Книги по алфавиту</title>
     *     <updated>2022-01-27T12:49:01+01:00</updated>
     *     <icon>/favicon.ico</icon>
     *     <link href="/opds-opensearch.xml" rel="search" type="application/opensearchdescription+xml"/>
     *     <link href="/opds/search?searchTerm={searchTerms}" rel="search" type="application/atom+xml"/>
     *     <link href="/opds" rel="start" type="application/atom+xml;profile=opds-catalog"/>
     *     <entry>
     *         <updated>2022-01-27T12:49:01+01:00</updated>
     *         <title>Идеалы бусидо: смерть, честь и преданность</title>
     *         <author>
     *             <name>Хёрст III Дж. Кэмерон</name>
     *             <uri>/a/189603</uri>
     *         </author>
     *         <author>
     *             <name>Куличкова Елена</name>
     *             <uri>/a/180220</uri>
     *         </author>
     *         <link href="/opds/author/189603" rel="related" type="application/atom+xml"
     *               title="Все книги автора Хёрст III Дж. Кэмерон"/>
     *         <category term="Военная история" label="Военная история"/>
     *         <category term="Востоковедение" label="Востоковедение"/>
     *         <category term="История" label="История"/>
     *         <dc:language>ru</dc:language>
     *         <dc:format>fb2+zip</dc:format>
     *         <content type="text/html">В данной статье я надеюсь достичь двух целей. Во-первых, я хотел бы обсудить концепцию
     *             бусидо и сам этот термин, поскольку как западное, так и японское понимание этого термина и ассоциированных с
     *             ним моральных ценностей были значительно искажены как в письменных источниках, опубликованных в обеих
     *             странах, так и в событиях современной истории. Затем я хотел бы исследовать часто связанные друг с другом
     *             понятия преданности, чести и смерти в средневековой и ранней современной Японии чтобы определиться, в
     *             действительности ли был некий постоянный взгляд на эти ценности, в особенности в том смысле, в котором эти
     *             ценности подпадают под понятие бусидо.&lt;br/&gt;Формат: fb2&lt;br/&gt;Язык: ru&lt;br/&gt;Размер: 94 Kb&lt;br/&gt;Скачиваний:
     *             1088&lt;br/&gt;
     *         </content>
     *         <link href="/b/500150/fb2" rel="http://opds-spec.org/acquisition/open-access" type="application/fb2+zip"/>
     *         <link href="/b/500150/html" rel="http://opds-spec.org/acquisition/open-access" type="application/html+zip"/>
     *         <link href="/b/500150/txt" rel="http://opds-spec.org/acquisition/open-access" type="application/txt+zip"/>
     *         <link href="/b/500150/rtf" rel="http://opds-spec.org/acquisition/open-access" type="application/rtf+zip"/>
     *         <link href="/b/500150/epub" rel="http://opds-spec.org/acquisition/open-access" type="application/epub+zip"/>
     *         <link href="/b/500150/mobi" rel="http://opds-spec.org/acquisition/open-access"
     *               type="application/x-mobipocket-ebook"/>
     *         <link href="/b/500150" rel="alternate" type="text/html" title="Книга на сайте"/>
     *         <id>tag:book:cf5c3a9d2e3c89748106160c9e64e9a6</id>
     *     </entry>
     * </feed>
     * @param authorId
     * @return
     * @throws FeedException
     */
    @GetMapping(value = "/author/{id}/alphabet")
    public Feed bookByAlphabet(@PathVariable(name = "id") Integer authorId) {
        return service.getBooksByAlphabet(authorId);
    }

}
