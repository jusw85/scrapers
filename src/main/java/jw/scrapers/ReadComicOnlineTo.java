package jw.scrapers;

import com.gargoylesoftware.htmlunit.Page;
import com.gargoylesoftware.htmlunit.WebClient;
import io.mikael.urlbuilder.UrlBuilder;
import jw.util.DownloadUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jsoup.nodes.Document;
import picocli.CommandLine;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Paths;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ReadComicOnlineTo implements AutoCloseable {

    public static void main(String[] args) {
        try {
            CommandLine.call(new ReadComicOnlineToCallable(), System.out, args);
        } catch (Exception e) {
            Throwable cause = e.getCause();
            LOGGER.error(cause);
        }
    }

    @CommandLine.Command(
            name = "readcomiconlineto",
            version = "0.1.0",
            description = "%nDownloads single chapters from readcomiconline.to.%n" +
                    "For personal use only. Use responsibly!%n",
            footerHeading = "%nExamples%n",
            footer = "# Download one chapter%n" +
                    "$ readcomiconline http://readcomiconline.to/Comic/{name}/{chapter}?id={id}",
            sortOptions = false,
            requiredOptionMarker = '*',
            abbreviateSynopsis = true)
    private static class ReadComicOnlineToCallable implements Callable<Void> {
        @CommandLine.Parameters(index = "0", arity = "1", description = "url of first page of chapter")
        private String url;

        @CommandLine.Option(names = {"-o", "--output"}, description = "output directory (default: out)")
        private File outDir = new File("out");

        @CommandLine.Option(names = {"--original"}, description = "use original filename")
        private boolean useOriginalFilename = false;

        @CommandLine.Option(names = {"-d", "--delay"}, description = "delay between each download in milliseconds (default: 1500)")
        private long delay = 1500L;

        @CommandLine.Option(names = {"-r", "--retries"}, description = "number of retries per download (default: 3)")
        private int retries = 3;

        @CommandLine.Option(names = {"-h", "--help"}, usageHelp = true, description = "show this help message and exit")
        private boolean helpRequested;

        @CommandLine.Option(names = {"-V", "--version"}, versionHelp = true, description = "display version info")
        private boolean versionInfoRequested;

        @Override
        public Void call() throws Exception {
            DownloadUtil downloadUtil = new DownloadUtil();
            downloadUtil.setNumRetries(retries);
            downloadUtil.setDelay(delay);
            try (ReadComicOnlineTo obj = new ReadComicOnlineTo(outDir, downloadUtil, useOriginalFilename)) {
                obj.download(url);
            } catch (Exception e) {
                throw e;
            }
            return null;
        }
    }

    private static final Logger LOGGER = LogManager.getLogger();

    private File outDir;
    private DownloadUtil downloadUtil;
    private boolean useOriginalFilename;

    public ReadComicOnlineTo(File outDir, DownloadUtil downloadUtil, boolean useOriginalFilename) {
        this.outDir = outDir;
        this.downloadUtil = downloadUtil;
        this.useOriginalFilename = useOriginalFilename;
    }

    private Pattern VALID_URL = Pattern.compile(
            "(?:https?://)?(?:www\\.)?readcomiconline\\.to/Comic/" +
                    "([^/]+)/([^\\?]+)\\?id=\\d+.*");

    public void download(String url) throws IOException, URISyntaxException {
        Matcher m = VALID_URL.matcher(url);
        if (!m.matches()) {
            throw new IllegalArgumentException("Unrecognized URL format");
        }
        String name = m.group(1);
        String chapter = m.group(2);
        File outChapterDir = Paths.get(outDir.toString(), name, chapter).toFile();

        UrlBuilder builder = UrlBuilder.fromString(url)
                .setParameter("quality", "hq")
                .setParameter("readType", "1");
        url = builder.toString();

        WebClient webClient = downloadUtil.getWebClient();
        webClient.getOptions().setJavaScriptEnabled(true);
        webClient.getOptions().setThrowExceptionOnFailingStatusCode(false);
        webClient.getOptions().setRedirectEnabled(true);
        downloadUtil.getPage(new URL(url));

        Page page;
        int statusCode;
        do {
            LOGGER.info("Waiting for Cloudflare");
            try {
                Thread.sleep(1000L);
            } catch (InterruptedException e) {
                LOGGER.error(e);
            }
            page = webClient.getCurrentWindow().getEnclosedPage();
            statusCode = page.getWebResponse().getStatusCode();
        } while (statusCode != 200);

        Document doc = downloadUtil.getDocument(page);

        Pattern p = Pattern.compile("lstImages.push\\(\"([^\"]+)\"\\);");
        m = p.matcher(doc.toString());
        int pageNumber = 1;
        while (m.find()) {
            URL imgurl = new URL(m.group(1));
            if (useOriginalFilename) {
                downloadUtil.downloadFileOriginalName(imgurl, outChapterDir, false);
            } else {
                String imgFilename = String.valueOf(pageNumber++);
                downloadUtil.downloadFileGuessExtension(imgurl, outChapterDir, imgFilename);
            }
        }
    }

    @Override
    public void close() throws Exception {
        downloadUtil.close();
    }

}
