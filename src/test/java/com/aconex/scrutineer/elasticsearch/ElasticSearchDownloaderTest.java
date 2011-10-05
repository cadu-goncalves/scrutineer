package com.aconex.scrutineer.elasticsearch;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;

import org.elasticsearch.action.ListenableActionFuture;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.client.action.search.SearchRequestBuilder;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

public class ElasticSearchDownloaderTest {

    private static final String INDEX_NAME = "indexName";
    private static final String ID = "123";
    private static final long VERSION = 123L;
    @Mock
    private TransportClient client;
    @Mock
    private SearchRequestBuilder searchRequestBuilder;

    @Mock @SuppressWarnings("unchecked")
    private ListenableActionFuture listenableActionFuture;

    @Mock
    private SearchResponse firstSearchResponse;
    @Mock
    private SearchResponse secondSearchResponse;

    @Mock
    private SearchHits firstHits;
    @Mock
    private SearchHits secondHits;

    @Mock
    private SearchHit firstBatchHit;
    @Mock
    private SearchHit secondBatchHit;

    private ByteArrayOutputStream outputStream;

    @Before public void setup() {
        initMocks(this);
        outputStream = new ByteArrayOutputStream();
    }

    @Test public void shouldIterateOverResultsAndSendToOutputStream() throws IOException {
        ElasticSearchDownloader elasticSearchDownloader = spy(new ElasticSearchDownloader(client, INDEX_NAME));
        doReturn(firstSearchResponse).when(elasticSearchDownloader).startScroll();
        doReturn(secondSearchResponse).when(elasticSearchDownloader).nextBatch();
        doReturn(true).when(elasticSearchDownloader).isCompleted();

        when(firstSearchResponse.getHits()).thenReturn(firstHits);
        when(firstHits.hits()).thenReturn(new SearchHit[]{firstBatchHit});
        when(firstBatchHit.getId()).thenReturn(ID);
        when(firstBatchHit.getVersion()).thenReturn(VERSION);

        when(secondSearchResponse.getHits()).thenReturn(secondHits);
        when(secondHits.hits()).thenReturn(new SearchHit[0]);

        elasticSearchDownloader.downloadTo(outputStream);

        ObjectInputStream objectInputStream = new ObjectInputStream(new ByteArrayInputStream(outputStream.toByteArray()));

        assertThat(objectInputStream.readLong(), is(Long.valueOf(ID)));
        assertThat(objectInputStream.readLong(), is(VERSION));

        // should assert the stream is complete, which alas for ObjectOutputStream, is an EOFException trap..
        try {
            objectInputStream.readInt();
            fail("Should have EOF'd to indicate no more data to read");
        } catch (EOFException eof) {

        }

    }

    @SuppressWarnings("unchecked")
    @Test public void shouldDoElasticSearchRequest() {
        when(client.prepareSearch(INDEX_NAME)).thenReturn(searchRequestBuilder);
        when(searchRequestBuilder.execute()).thenReturn(listenableActionFuture);
        when(listenableActionFuture.actionGet()).thenReturn(firstSearchResponse);
        ElasticSearchDownloader elasticSearchDownloader = new ElasticSearchDownloader(client, INDEX_NAME);
        assertThat(elasticSearchDownloader.startScroll(), is(firstSearchResponse));
        verify(searchRequestBuilder).setSearchType(SearchType.SCAN);
        verify(searchRequestBuilder).setNoFields();
        verify(searchRequestBuilder).setVersion(true);
    }

}
