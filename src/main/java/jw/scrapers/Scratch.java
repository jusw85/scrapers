package jw.scrapers;

import com.gargoylesoftware.htmlunit.BrowserVersion;
import com.gargoylesoftware.htmlunit.HttpMethod;
import com.gargoylesoftware.htmlunit.ProxyConfig;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.WebRequest;

import java.io.IOException;
import java.net.URL;

public class Scratch {

    public static void main(String[] args) throws Exception {
    }

    /**
     * use # nc -l 5000 to check actual request
     */
    public static void checkRequest() throws IOException {
        WebClient wc = new WebClient(BrowserVersion.FIREFOX_52);
        ProxyConfig proxyConfig = new ProxyConfig("127.0.0.1", 5000);
        wc.getOptions().setProxyConfig(proxyConfig);
        URL url = new URL("http://www.example.org");
        WebRequest request = new WebRequest(url, HttpMethod.GET);
        wc.getPage(request);
    }

}
