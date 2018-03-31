package jw.scrapers;

import com.gargoylesoftware.htmlunit.Page;
import com.gargoylesoftware.htmlunit.html.HtmlAnchor;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.google.common.io.Files;
import jw.util.DownloadUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import picocli.CommandLine;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Paths;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MangaHere implements AutoCloseable {

    public static void main(String[] args) {
        try {
            CommandLine.call(new MangaHereCallable(), System.out, args);
        } catch (Exception e) {
            Throwable cause = e.getCause();
            LOGGER.error(cause);
        }
    }

    @CommandLine.Command(
            name = "mangahere",
            version = "0.1.0",
            description = "%nDownloads single chapters from MangaHere.%n" +
                    "For personal use only. Use responsibly!%n",
            footerHeading = "%nExamples%n",
            footer = "# Download one chapter%n" +
                    "$ mangahere http://www.mangahere.cc/manga/{name}/{chapter}%n%n" +
                    "# Download multiple chapters%n" +
                    "$ for i in {1..5}; do mangahere http://www.mangahere.cc/manga/{name}/c${i}; done",
            sortOptions = false,
            requiredOptionMarker = '*',
            abbreviateSynopsis = true)
    private static class MangaHereCallable implements Callable<Void> {
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
            try (MangaHere obj = new MangaHere(outDir, downloadUtil, useOriginalFilename)) {
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

    public MangaHere(File outDir, DownloadUtil downloadUtil, boolean useOriginalFilename) {
        this.outDir = outDir;
        this.downloadUtil = downloadUtil;
        this.useOriginalFilename = useOriginalFilename;
    }

    private Pattern VALID_URL = Pattern.compile(
            "((?:https?://)?(?:www\\.)?mangahere\\.cc/manga/" +
                    "([^/]+)/((?:v[^/]+/)?c[^/]+))(?:/|/\\d+\\.html)?");

    public void download(String url) throws IOException, URISyntaxException {
        Matcher m = VALID_URL.matcher(url);
        if (!m.matches()) {
            throw new IllegalArgumentException("Unrecognized URL format");
        }
        String baseurl = m.group(1);
        String name = m.group(2);
        String chapter = m.group(3);
        int pageNumber = 1;

        File outChapterDir = Paths.get(outDir.toString(), name, chapter).toFile();
        Page page = downloadUtil.getPage(new URL(baseurl));
        downloadRecursive(page, chapter, outChapterDir, pageNumber);
    }

    public void downloadRecursive(Page page, String chapter, File outChapterDir, int pageNumber) throws IOException, URISyntaxException {
        Matcher m = VALID_URL.matcher(page.getUrl().toString());
        if (!(m.matches() && m.group(3).equals(chapter))) {
            return;
        }
        HtmlPage htmlpage = (HtmlPage) page;
        HtmlAnchor anchor = htmlpage.querySelector("a.next_page");

        Document doc = DownloadUtil.getDocument(page);
        Element elem = doc.selectFirst("section#viewer > a");
        if (elem == null) {
            LOGGER.info("Invalid html(possibly advertisement), refreshing page");
            downloadRecursive(htmlpage.refresh(), chapter, outChapterDir, pageNumber);
            return;
        }
        Element img = elem.select("> img").get(1);
        URL imgurl = new URL(img.attr("abs:src"));

        if (useOriginalFilename) {
            downloadUtil.downloadFileOriginalName(imgurl, outChapterDir, false);
        } else {
            String imgFilename = String.valueOf(pageNumber);
            downloadUtil.downloadFileGuessExtension(imgurl, outChapterDir, imgFilename);
        }
        downloadRecursive(anchor.click(), chapter, outChapterDir, ++pageNumber);
    }

    @Override
    public void close() throws Exception {
        downloadUtil.close();
    }

}
