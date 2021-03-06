package de.ids.mannheim.clarin.teispeech.service;

import de.ids.mannheim.clarin.mime.MIMETypes;
import de.ids.mannheim.clarin.teispeech.data.GATParser;
import de.ids.mannheim.clarin.teispeech.data.LanguageDetect;
import de.ids.mannheim.clarin.teispeech.tools.ProcessingLevel;
import de.ids.mannheim.clarin.teispeech.utilities.ServiceUtilities;
import de.ids.mannheim.clarin.teispeech.workflow.*;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.jdom2.JDOMException;
import org.korpora.useful.Anonymize;
import org.korpora.useful.XMLUtilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Webservices for dealing with TEI-encoded documents
 *
 * @author bfi
 */

@Path("")
public class TEILicht {

    private final static Logger LOGGER = LoggerFactory
            .getLogger(TEILicht.class.getName());

    /**
     * convert to a TEI ISO transcription:
     *
     * @param input
     *     the input document – plain text!
     * @param language
     *     the presumed language, preferably a ISO 639 code
     * @param lang
     *     (alternative parameter name) the presumed language, preferably a ISO
     *     639 code
     * @param request
     *     the HTTP request
     * @return a TEI-encoded speech transcription
     */
    @POST
    @Path("text2iso")
    @Consumes({ MIMETypes.PLAIN_TEXT, MIMETypes.SIMPLE_EXMARALDA })
    @Produces({ MIMETypes.TEI_SPOKEN, MIMETypes.DTA, MIMETypes.TEI,
            MIMETypes.XML })

    public Response text2iso(InputStream input, @QueryParam("lang") String lang,
            @QueryParam("language") String language,
            @Context HttpServletRequest request) {
        try {
            if (language == null || "".equals(language)) {
                language = lang;
            }
            ServiceUtilities.checkLanguage(language);
            LOGGER.info("TEILICHT text2iso <{}> of length {} for {}.",
                    request.getHeader(HttpHeaders.CONTENT_TYPE),
                    request.getHeader(HttpHeaders.CONTENT_LENGTH),
                    Anonymize.anonymizeAddress(request));
            CharStream inputCS;
            inputCS = CharStreams.fromStream(input);
            Document doc = TextToTEIConversion.process(inputCS, language);
            return Response.ok(doc, request.getContentType()).build();
        } catch (IOException e) {
            throw new WebApplicationException(e,
                    Response.status(400).entity(e.getMessage()).build());
        }
    }

    /**
     * segmentize TEI ISO document according to transcription conventions:
     *
     * @param input
     *     a TEI-encoded speech transcription
     * @param level
     *     the parsing level: generic, minimal, basic
     * @param request
     *     the HTTP request
     * @return a TEI-encoded speech transcription with annotation parsed
     */
    @POST
    @Path("segmentize")
    @Consumes({ MIMETypes.TEI_SPOKEN, MIMETypes.DTA, MIMETypes.TEI,
            MIMETypes.XML })
    @Produces({ MIMETypes.TEI_SPOKEN, MIMETypes.DTA, MIMETypes.TEI,
            MIMETypes.XML })

    public Response segmentize(InputStream input,
            @DefaultValue("generic") @QueryParam("level") ProcessingLevel level,
            @Context HttpServletRequest request) {
        if (level == ProcessingLevel.generic)
            try {
                DocumentBuilderFactory factory = DocumentBuilderFactory
                        .newInstance();
                factory.setNamespaceAware(true);
                DocumentBuilder builder;
                builder = factory.newDocumentBuilder();
                Document doc = builder.parse(input);
                GenericParsing.process(doc);
                return Response.ok(doc, request.getContentType()).build();
            } catch (SAXException | IOException
                    | ParserConfigurationException e) {
                throw new WebApplicationException(e,
                        Response.status(400).entity(e.getMessage()).build());
            }
        else
            try {
                org.jdom2.Document doc = XMLUtilities.parseXMLviaJDOM(input);
                GATParser parser = new GATParser();
                parser.parseDocument(doc, level.ordinal() + 1);
                return Response.ok(XMLUtilities.convertJDOMToDOM(doc),
                        request.getContentType()).build();
            } catch (IllegalArgumentException | IOException | JDOMException e) {
                throw new WebApplicationException(e,
                        Response.status(400).entity(e.getMessage()).build());
            }
    }

    /**
     * convert from plain text to a TEI ISO transcription:
     * segmentize TEI ISO document according to transcription conventions:
     *
     * @param input
     *     the input document – plain text!
     * @param language
     *     the presumed language, preferably a ISO 639 code
     * @param lang
     *     (alternative parameter name) the presumed language, preferably a ISO
     *     639 code
     * @param level
     *     the parsing level: generic, minimal, basic
     * @param request
     *     the HTTP request
     * @return a TEI-encoded speech transcription with annotation parsed
     */
    @POST
    @Path("text2seg")
    @Consumes({ MIMETypes.PLAIN_TEXT, MIMETypes.SIMPLE_EXMARALDA })
    @Produces({ MIMETypes.TEI_SPOKEN, MIMETypes.DTA, MIMETypes.TEI,
            MIMETypes.XML })

    public Response text2seg(InputStream input, @QueryParam("lang") String lang,
            @QueryParam("language") String language,
            @DefaultValue("generic") @QueryParam("level") ProcessingLevel level,
            @Context HttpServletRequest request) {
        try {
            if (language == null || "".equals(language)) {
                language = lang;
            }
            ServiceUtilities.checkLanguage(language);
            LOGGER.info("TEILICHT text2seg <{}> of length {} for {}.",
                    request.getHeader(HttpHeaders.CONTENT_TYPE),
                    request.getHeader(HttpHeaders.CONTENT_LENGTH),
                    Anonymize.anonymizeAddress(request));
            CharStream inputCS;
            inputCS = CharStreams.fromStream(input);
            Document doc = TextToTEIConversion.process(inputCS, language);
            if (level == ProcessingLevel.generic) {
                GenericParsing.process(doc);
                return Response.ok(doc, request.getContentType()).build();
            }
            else
                try {
                    org.jdom2.Document jDoc = XMLUtilities.convertDOMtoJDOM(doc);
                    GATParser parser = new GATParser();
                    parser.parseDocument(jDoc, level.ordinal() + 1);
                    return Response.ok(XMLUtilities.convertJDOMToDOM(jDoc),
                            request.getContentType()).build();
                } catch (IllegalArgumentException | IOException | JDOMException e) {
                    throw new WebApplicationException(e,
                            Response.status(400).entity(e.getMessage()).build());
                }
        } catch (IOException e) {
            throw new WebApplicationException(e,
                    Response.status(400).entity(e.getMessage()).build());
        }
    }


    /**
     * normalize using an EXMARaLDA-OrthoNormal-based normalizer:
     *
     * @param input
     *     a TEI-encoded speech transcription
     * @param language
     *     the presumed language, preferably a ISO 639 code
     * @param lang
     *     (alternative parameter name) the presumed language, preferably a ISO
     *     639 code
     * @param keepCase
     *     if true, do not convert to lower case when normalizing and
     *     effectively skip capitalized words
     * @param force
     *     whether to force normalization
     * @param request
     *     the HTTP request
     * @return a TEI-encoded speech transcription with normalization in
     * &lt;w&gt;
     */
    @POST
    @Path("normalize")
    @Consumes({ MIMETypes.TEI_SPOKEN, MIMETypes.DTA, MIMETypes.TEI,
            MIMETypes.XML })
    @Produces({ MIMETypes.TEI_SPOKEN, MIMETypes.DTA, MIMETypes.TEI,
            MIMETypes.XML })

    public Response normalize(InputStream input,
            @QueryParam("lang") String lang,
            @QueryParam("language") String language,
            @QueryParam("keep_case") boolean keepCase,
            @QueryParam("force") boolean force,
            @Context HttpServletRequest request) {
        try {
            if (language == null || "".equals(language)) {
                language = lang;
            }
            ServiceUtilities.checkLanguage(language);
            DocumentBuilderFactory factory = DocumentBuilderFactory
                    .newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder;
            builder = factory.newDocumentBuilder();
            Document doc = builder.parse(input);
            DictionaryNormalizer diNo = new DictionaryNormalizer(keepCase,
                    false);
            TEINormalizer teiDictNormalizer = new TEINormalizer(diNo, language);
            LOGGER.info("TEILICHT normalize <{}> of length {} for {}.",
                    request.getHeader(HttpHeaders.CONTENT_TYPE),
                    request.getHeader(HttpHeaders.CONTENT_LENGTH),
                    Anonymize.anonymizeAddress(request));
            teiDictNormalizer.normalize(doc, force);
            return Response.ok(doc, request.getContentType()).build();
        } catch (IllegalArgumentException | SAXException
                | ParserConfigurationException | IOException e) {
            throw new WebApplicationException(e,
                    Response.status(400).entity(e.getMessage()).build());
        }
    }

    /**
     * POS-tag a TEI ISO transcription:
     *
     * @param input
     *     a TEI-encoded speech transcription
     * @param language
     *     the presumed language, preferably a ISO 639 code
     * @param lang
     *     (alternative parameter name) the presumed language, preferably a ISO
     *     639 code
     * @param force
     *     whether to force normalization
     * @param request
     *     the HTTP request
     * @return a TEI-encoded speech transcription with POS tags
     */
    @POST
    @Path("pos")
    @Consumes({ MIMETypes.TEI_SPOKEN, MIMETypes.DTA, MIMETypes.TEI,
            MIMETypes.XML })
    @Produces({ MIMETypes.TEI_SPOKEN, MIMETypes.DTA, MIMETypes.TEI,
            MIMETypes.XML })

    public Response pos(InputStream input, @QueryParam("lang") String lang,
            @QueryParam("language") String language,
            @QueryParam("force") boolean force,
            @Context HttpServletRequest request) {
        try {
            if (language == null || "".equals(language)) {
                language = lang;
            }
            ServiceUtilities.checkLanguage(language);
            DocumentBuilderFactory factory = DocumentBuilderFactory
                    .newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder;
            builder = factory.newDocumentBuilder();
            Document doc = builder.parse(input);
            TEIPOS teipo = new TEIPOS(doc, language);
            LOGGER.info("TEILICHT pos <{}> of length {} for {}.",
                    request.getHeader(HttpHeaders.CONTENT_TYPE),
                    request.getHeader(HttpHeaders.CONTENT_LENGTH),
                    Anonymize.anonymizeAddress(request));
            teipo.posTag(force);
            return Response.ok(doc, request.getContentType()).build();
        } catch (IllegalArgumentException | SAXException
                | ParserConfigurationException | IOException e) {
            throw new WebApplicationException(e,
                    Response.status(400).entity(e.getMessage()).build());
        }
    }

    /**
     * Detect languages in a a TEI ISO transcription:
     *
     * @param input
     *     a TEI-encoded speech transcription
     * @param expected
     *     the languages expected in the document
     * @param expected1
     *     expected language 1, as WebLicht can't handle lists
     * @param expected2
     *     expected language 2, as WebLicht can't handle lists
     * @param expected3
     *     expected language 3, as WebLicht can't handle lists
     * @param expected4
     *     expected language 4, as WebLicht can't handle lists
     * @param expected5
     *     expected language 5, as WebLicht can't handle lists
     * @param language
     *     the presumed language, preferably a ISO 639 code
     * @param lang
     *     (alternative parameter name) the presumed language, preferably a ISO
     *     639 code
     * @param force
     *     whether to force normalization
     * @param minimalLength
     *     the minimal length of an utterance to attempt language detection
     * @param request
     *     the HTTP request
     * @return a TEI-encoded speech transcription with languages detected
     */
    @POST
    @Path("guess")
    @Consumes({ MIMETypes.TEI_SPOKEN, MIMETypes.DTA, MIMETypes.TEI,
            MIMETypes.XML })
    @Produces({ MIMETypes.TEI_SPOKEN, MIMETypes.DTA, MIMETypes.TEI,
            MIMETypes.XML })

    public Response guess(InputStream input, @QueryParam("lang") String lang,
            @QueryParam("language") String language,
            @QueryParam("expected") List<String> expected,
            @QueryParam("expected1") String expected1,
            @QueryParam("expected2") String expected2,
            @QueryParam("expected3") String expected3,
            @QueryParam("expected4") String expected4,
            @QueryParam("expected5") String expected5,
            @QueryParam("force") boolean force,
            @QueryParam("minimal") @DefaultValue("5") int minimalLength,
            @Context HttpServletRequest request) {
        try {
            if (language == null || "".equals(language)) {
                language = lang;
            }
            ServiceUtilities.checkLanguage(language);
            for (String l : new String[] { expected1, expected2, expected3,
                    expected4, expected5 }) {
                expected.add(l);
            }
            List<String> expectedLangs = expected.stream()
                    .filter(l -> l != null && !l.isEmpty() && !"".equals(l))
                    .map(ServiceUtilities::checkLanguage)
                    .collect(Collectors.toList());
            if (expectedLangs.size() == 1) {
                expectedLangs = Arrays
                        .asList(expectedLangs.get(0).split("[, ]"));
            }
            DocumentBuilderFactory factory = DocumentBuilderFactory
                    .newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder;
            builder = factory.newDocumentBuilder();
            Document doc = builder.parse(input);
            LanguageDetect guesser = new LanguageDetect(doc, language,
                    expectedLangs, minimalLength);
            LOGGER.info(
                    "TEILICHT guess <{}> of length {} for {} [minimal length: {}].",
                    request.getHeader(HttpHeaders.CONTENT_TYPE),
                    request.getHeader(HttpHeaders.CONTENT_LENGTH),
                    Anonymize.anonymizeAddress(request), minimalLength);
            LOGGER.info("expected: {}", expectedLangs);
            guesser.detect(force);
            return Response.ok(doc, request.getContentType()).build();
        } catch (IllegalArgumentException | SAXException
                | ParserConfigurationException | IOException e) {
            throw new WebApplicationException(e,
                    Response.status(400).entity(e.getMessage()).build());
        }
    }

    /**
     * Detect languages in a a TEI ISO transcription:
     *
     * @param input
     *     a TEI-encoded speech transcription
     * @param language
     *     the presumed language, preferably a ISO 639 code
     * @param lang
     *     (alternative parameter name) the presumed language, preferably a ISO
     *     639 code
     * @param transcribe
     *     whether to add a phonetic transcription to the utterances if possible
     * @param usePhones
     *     whether to use (pseudo)phones to determine relative duration of words
     * @param force
     *     whether to force normalization
     * @param request
     *     the HTTP request
     * @param time
     *     the time length of the conversation in seconds
     * @param offset
     *     the time offset in seconds
     * @param every
     *     number of items after which to insert an orientation anchor
     * @return a TEI-encoded speech transcription with languages detected
     */
    @POST
    @Path("align")
    @Consumes({ MIMETypes.TEI_SPOKEN, MIMETypes.DTA, MIMETypes.TEI,
            MIMETypes.XML })
    @Produces({ MIMETypes.TEI_SPOKEN, MIMETypes.DTA, MIMETypes.TEI,
            MIMETypes.XML })

    public Response align(InputStream input, @QueryParam("lang") String lang,
            @QueryParam("language") String language,
            @QueryParam("transcribe") boolean transcribe,
            @QueryParam("use_phones") boolean usePhones,
            @QueryParam("force") boolean force,
            @QueryParam("time") @DefaultValue("-1.0") double time,
            @QueryParam("offset") @DefaultValue("0.0") double offset,
            @QueryParam("every") @DefaultValue("5") int every,
            @Context HttpServletRequest request) {
        try {
            if (language == null || "".equals(language)) {
                language = lang;
            }
            ServiceUtilities.checkLanguage(language);
            DocumentBuilderFactory factory = DocumentBuilderFactory
                    .newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder;
            builder = factory.newDocumentBuilder();
            Document doc = builder.parse(input);
            PseudoAlign aligner = new PseudoAlign(doc, language, usePhones,
                    transcribe, force, time, offset, every);
            LOGGER.info(
                    "TeiLicht ALIGN <{}> of length {} for {}. [force: {}, use_phones: {}, transcribe: {}]",
                    request.getHeader(HttpHeaders.CONTENT_TYPE),
                    request.getHeader(HttpHeaders.CONTENT_LENGTH),
                    Anonymize.anonymizeAddress(request), force, usePhones,
                    transcribe);
            LOGGER.info("force: {}, use_phones: {}, transcribe: {} [{}]",
                    request.getParameter("force"),
                    request.getParameter("use_phones"),
                    request.getParameter("transcribe"),
                    request.getParameterMap().keySet());
            aligner.calculateUtterances();
            return Response.ok(aligner.getDoc(), request.getContentType())
                    .build();
        } catch (IllegalArgumentException | SAXException
                | ParserConfigurationException | IOException e) {
            throw new WebApplicationException(e,
                    Response.status(400).entity(e.getMessage()).build());
        }
    }

    /**
     * add IDs to XML elements for roundtripping
     *
     * @param input
     *     a TEI-encoded speech transcription
     * @param request
     *     the HTTP request
     * @return a TEI-encoded speech transcription with IDs
     */
    @POST
    @Path("identify")
    @Consumes({ MIMETypes.TEI_SPOKEN, MIMETypes.DTA, MIMETypes.TEI,
            MIMETypes.XML })
    @Produces({ MIMETypes.TEI_SPOKEN, MIMETypes.DTA, MIMETypes.TEI,
            MIMETypes.XML })

    public Response identify(InputStream input,
            @Context HttpServletRequest request) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory
                    .newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder;
            builder = factory.newDocumentBuilder();
            Document doc = builder.parse(input);
            DocumentIdentifier.makeIDs(doc);
            LOGGER.info("TEILICHT identify <{}> of length {} for {}.",
                    request.getHeader(HttpHeaders.CONTENT_TYPE),
                    request.getHeader(HttpHeaders.CONTENT_LENGTH),
                    Anonymize.anonymizeAddress(request));
            return Response.ok(doc, request.getContentType()).build();
        } catch (IllegalArgumentException | SAXException
                | ParserConfigurationException | IOException e) {
            throw new WebApplicationException(e,
                    Response.status(400).entity(e.getMessage()).build());
        }
    }

    /**
     * remove IDs from XML elements which are only used in roundtripping
     *
     * @param input
     *     a TEI-encoded speech transcription
     * @param request
     *     the HTTP request
     * @return a TEI-encoded speech transcription without IDs
     */
    @POST
    @Path("unidentify")
    @Consumes({ MIMETypes.TEI_SPOKEN, MIMETypes.DTA, MIMETypes.TEI,
            MIMETypes.XML })
    @Produces({ MIMETypes.TEI_SPOKEN, MIMETypes.DTA, MIMETypes.TEI,
            MIMETypes.XML })

    public Response unidentify(InputStream input,
            @Context HttpServletRequest request) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory
                    .newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder;
            builder = factory.newDocumentBuilder();
            Document doc = builder.parse(input);
            DocumentIdentifier.removeIDs(doc);
            LOGGER.info("TEILICHT unidentify <{}> of length {} for {}.",
                    request.getHeader(HttpHeaders.CONTENT_TYPE),
                    request.getHeader(HttpHeaders.CONTENT_LENGTH),
                    Anonymize.anonymizeAddress(request));
            return Response.ok(doc, request.getContentType()).build();
        } catch (IllegalArgumentException | SAXException
                | ParserConfigurationException | IOException e) {
            throw new WebApplicationException(e,
                    Response.status(400).entity(e.getMessage()).build());
        }
    }
}
