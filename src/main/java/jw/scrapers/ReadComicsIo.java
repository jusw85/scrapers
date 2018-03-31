package jw.scrapers;

import jw.util.DownloadUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import picocli.CommandLine;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Paths;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ReadComicsIo implements AutoCloseable {

    public static void main(String[] args) {
        try {
            CommandLine.call(new ReadComicsIoCallable(), System.out, args);
        } catch (Exception e) {
            Throwable cause = e.getCause();
            LOGGER.error(cause);
        }
    }

    @CommandLine.Command(
            name = "readcomicsio",
            version = "0.1.0",
            description = "%nDownloads single chapters from readcomics.io.%n" +
                    "For personal use only. Use responsibly!%n",
            footerHeading = "%nExamples%n",
            footer = "# Download one chapter%n" +
                    "$ readcomicsio https://www.readcomics.io/{name}/{chapter}",
            sortOptions = false,
            requiredOptionMarker = '*',
            abbreviateSynopsis = true)
    private static class ReadComicsIoCallable implements Callable<Void> {
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
            try (ReadComicsIo obj = new ReadComicsIo(outDir, downloadUtil, useOriginalFilename)) {
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

    public ReadComicsIo(File outDir, DownloadUtil downloadUtil, boolean useOriginalFilename) {
        this.outDir = outDir;
        this.downloadUtil = downloadUtil;
        this.useOriginalFilename = useOriginalFilename;
    }

    private Pattern VALID_URL = Pattern.compile(
            "((?:https?://)?(?:www\\.)?readcomics\\.io/" +
                    "([^/]+)/([^/]+))(?:/.*)?");

    public void download(String url) throws IOException, URISyntaxException {
        Matcher m = VALID_URL.matcher(url);
        if (!m.matches()) {
            throw new IllegalArgumentException("Unrecognized URL format");
        }
        String baseurl = m.group(1);
        String name = m.group(2);
        String chapter = m.group(3);
        url = baseurl + "/full";
        File outChapterDir = Paths.get(outDir.toString(), name, chapter).toFile();

        Document document = downloadUtil.getDocument(new URL(url));
        Elements elems = document.select("div.chapter-container").select("img");
        int pageNumber = 1;
        for (Element elem : elems) {
            URL imgurl = new URL(elem.attr("abs:src"));
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
