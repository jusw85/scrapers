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
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

public class Khinsider implements AutoCloseable {

    public static void main(String[] args) {
        try {
            CommandLine.call(new KhinsiderCallable(), System.out, args);
        } catch (Exception e) {
            Throwable cause = e.getCause();
            LOGGER.error(cause);
        }
    }

    @CommandLine.Command(
            name = "khinsider",
            version = "0.1.0",
            description = "%nDownloads soundtracks from KHInsider.%n" +
                    "For personal use only. Use responsibly!%n",
            footerHeading = "%nExamples%n",
            footer = "# Download all tracks%n" +
                    "$ khinsider https://downloads.khinsider.com/game-soundtracks/album/{name}",
            sortOptions = false,
            requiredOptionMarker = '*',
            abbreviateSynopsis = true)
    private static class KhinsiderCallable implements Callable<Void> {
        @CommandLine.Parameters(index = "0", arity = "1", description = "url of soundtrack")
        private String url;

        @CommandLine.Option(names = {"-o", "--output"}, description = "output directory (default: out)")
        private File outDir = new File("out");

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
            try (Khinsider khinsider = new Khinsider(outDir, downloadUtil)) {
                khinsider.downloadAll(url);
            } catch (Exception e) {
                throw e;
            }
            return null;
        }
    }

    private static final Logger LOGGER = LogManager.getLogger();

    private File outDir;
    private DownloadUtil downloadUtil;

    public Khinsider(File outDir, DownloadUtil downloadUtil) {
        this.outDir = outDir;
        this.downloadUtil = downloadUtil;
    }

    public void downloadAll(String url) throws IOException, URISyntaxException {
        Document doc = downloadUtil.getDocument(new URL(url));
        Elements hrefs = doc.getElementsByAttributeValueMatching("href", ".*mp3$");
        HashSet<String> links = hrefs.stream()
                .map(x -> x.attr("abs:href"))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        for (String link : links) {
            downloadOne(link);
        }
    }

    public void downloadOne(String url) throws IOException, URISyntaxException {
        Document doc = downloadUtil.getDocument(new URL(url));
        Elements hrefs = doc.select("a:containsOwn(Click here to download)");
        for (Element href : hrefs) {
            URL downloadUrl = new URL(href.attr("abs:href"));
            downloadUtil.downloadFileOriginalName(downloadUrl, outDir, false);
        }
    }

    @Override
    public void close() throws Exception {
        downloadUtil.close();
    }

}
