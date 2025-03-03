package com.eulerity.hackathon.imagefinder;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;

import com.google.gson.JsonObject;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import com.google.gson.JsonParser;
import com.google.gson.Gson;

public class ImageFinderTest {

    private HttpServletRequest request;
    private HttpServletResponse response;
    private StringWriter sw;
    private HttpSession session;
    private ImageFinder imageFinder;

    @Before
    public void setUp() throws Exception {
        request = Mockito.mock(HttpServletRequest.class);
        response = Mockito.mock(HttpServletResponse.class);
        sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        Mockito.when(response.getWriter()).thenReturn(pw);
        Mockito.when(request.getRequestURI()).thenReturn("/foo/foo/foo");
        Mockito.when(request.getRequestURL()).thenReturn(new StringBuffer("http://localhost:8080/foo/foo/foo"));
        session = Mockito.mock(HttpSession.class);
        Mockito.when(request.getSession()).thenReturn(session);

        // 初始化 ImageFinder 实例
        imageFinder = Mockito.spy(new ImageFinder());

        // **Mock `startCrawling(url)` 让它返回固定结果，而不是真正爬取**
        CompletableFuture<CrawlResult> mockFuture = CompletableFuture.completedFuture(
                new CrawlResult("completed", "Crawling completed successfully.",
                        new Gson().toJson(ImageFinder.testImages), "[]")
        );
        Mockito.doReturn(mockFuture).when(imageFinder).startCrawling(Mockito.anyString());
    }

    @Test
    public void testDoPostReturnsTestImages() throws IOException, ServletException {
        Mockito.when(request.getServletPath()).thenReturn("/main");
        Mockito.when(request.getParameter("url")).thenReturn("http://example.com");

        imageFinder.doPost(request, response);

        CrawlResult expectedResult = new CrawlResult(
                "completed",
                "Crawling completed successfully.",
                new Gson().toJson(ImageFinder.testImages),
                new Gson().toJson(new ArrayList<>())
        );

        JsonObject expectedJson = new JsonParser().parse(new Gson().toJson(expectedResult)).getAsJsonObject();
        JsonObject actualJson = new JsonParser().parse(sw.toString().trim()).getAsJsonObject();

        Assert.assertEquals(expectedJson, actualJson);

    }
}