package jw.util;

import com.gargoylesoftware.htmlunit.BrowserVersion;
import com.gargoylesoftware.htmlunit.FailingHttpStatusCodeException;
import com.gargoylesoftware.htmlunit.HttpMethod;
import com.gargoylesoftware.htmlunit.Page;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.WebRequest;
import com.google.common.io.Files;
import org.apache.http.conn.ConnectTimeoutException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLDecoder;
import java.nio.file.Paths;
import java.util.Iterator;

public class DownloadUtil implements AutoCloseable {

    private static final Logger LOGGER = LogManager.getLogger();

    private WebClient webClient;
    private int numRetries = 3;
    private long delay = 0L;

    public DownloadUtil() {
        webClient = getDefaultWebClient();
    }

    public DownloadUtil(WebClient webClient) {
        this.webClient = webClient;
    }

    public static WebClient getDefaultWebClient() {
        WebClient webClient = new WebClient(BrowserVersion.FIREFOX_52);
        webClient.getOptions().setThrowExceptionOnScriptError(false);
        return webClient;
    }

    public void downloadFileOriginalName(URL url, File outDir, boolean withPath) throws IOException {
        String path = URLDecoder.decode(url.getPath(), "UTF-8");
        String filename = withPath ? Paths.get(path).toString() : Paths.get(path).getFileName().toString();
        File file = new File(outDir, filename);
        downloadFile(url, file);
    }

    public void downloadFileGuessExtension(URL url, File outDir, String filenameNoExt) throws IOException {
        InputStream is = getStream(url);
        PushbackInputStream pis = new PushbackInputStream(is, 32);
        byte[] firstBytes = new byte[32];
        pis.read(firstBytes);
        pis.unread(firstBytes);

        ByteArrayInputStream bais = new ByteArrayInputStream(firstBytes);
        String mimeType = URLConnection.guessContentTypeFromStream(bais);
        String extension;
        boolean isImage = false;
        if (mimeType.equals("text/html")) {
            extension = "html";
        } else if (mimeType.equals("application/xml")) {
            extension = "xml";
        } else if (mimeType.startsWith("image")) {
            isImage = true;
            extension = "";
        } else {
            extension = Files.getFileExtension(url.getPath());
        }
        String filename = filenameNoExt + "." + extension;
        File file = new File(outDir, filename);
        Files.createParentDirs(file);
        Files.asByteSink(file).writeFrom(pis);

        if (isImage) {
            ImageInputStream iis = ImageIO.createImageInputStream(file);
            Iterator<ImageReader> it = ImageIO.getImageReaders(iis);
            if (it.hasNext()) {
                extension = it.next().getOriginatingProvider().getFileSuffixes()[0];
                filename = filenameNoExt + "." + extension;
                file.renameTo(new File(outDir, filename));
            }
        }
    }

    public void downloadFile(URL url, File file) throws IOException {
        InputStream is = getStream(url);
        Files.createParentDirs(file);
        Files.asByteSink(file).writeFrom(is);
    }

    public InputStream getStream(URL url) throws IOException {
        return getPage(url).getWebResponse().getContentAsStream();
    }

    public String getHtml(URL url) throws IOException {
        return getPage(url).getWebResponse().getContentAsString();
    }

    public Document getDocument(URL url) throws IOException, URISyntaxException {
        String html = getHtml(url);
        return getDocument(html, url);
    }

    public static Document getDocument(Page page) throws URISyntaxException {
        String html = page.getWebResponse().getContentAsString();
        URL url = page.getUrl();
        return getDocument(html, url);
    }

    private static Document getDocument(String html, URL url) throws URISyntaxException {
        Document doc = Jsoup.parse(html);
        URI baseUri = new URI(url.getProtocol(), url.getAuthority(), null, null, null);
        doc.setBaseUri(baseUri.toString());
        return doc;
    }

    public <P extends Page> P getPage(URL url) throws IOException {
        WebRequest request = new WebRequest(url, HttpMethod.GET);
        return getPage(request);
    }

    private <P extends Page> P getPage(WebRequest request) throws IOException {
        LOGGER.info("Grabbing {}", request.getUrl());
        int i = numRetries;

        P page = null;
        do {
            try {
                page = webClient.getPage(request);
            } catch (ConnectTimeoutException | SocketTimeoutException | FailingHttpStatusCodeException e) {
                LOGGER.warn(e);
                if (i-- <= 0) throw e;
            }
        } while (page == null);
        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            LOGGER.error(e);
        }
        return page;
    }

    @Override
    public void close() throws Exception {
        webClient.close();
    }

    public int getNumRetries() {
        return numRetries;
    }

    public void setNumRetries(int numRetries) {
        this.numRetries = numRetries;
    }

    public long getDelay() {
        return delay;
    }

    public void setDelay(long delay) {
        this.delay = delay;
    }

    public WebClient getWebClient() {
        return webClient;
    }

}
