package com.callcenter.search.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.GetResponse;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.json.JsonData;
import com.callcenter.search.model.CallRecordDetailView;
import com.callcenter.search.model.CallRecordQueryRequest;
import com.callcenter.search.model.CallRecordSearchSource;
import com.callcenter.search.model.CallRecordSortField;
import com.callcenter.search.model.CallRecordView;
import com.callcenter.search.model.CallRoundSearchSource;
import com.callcenter.search.model.CallRoundView;
import com.callcenter.search.model.PageResponse;
import com.callcenter.search.model.SortOrderType;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CallRecordQueryService {

    private static final String CALL_RECORD_READ_ALIAS = "call_record_read";
    private static final String CALL_ROUND_READ_ALIAS = "call_round_read";
    private static final Logger log = LoggerFactory.getLogger(CallRecordQueryService.class);

    private final ElasticsearchClient elasticsearchClient;

    public CallRecordQueryService(ElasticsearchClient elasticsearchClient) {
        this.elasticsearchClient = elasticsearchClient;
    }

    public PageResponse<CallRecordView> query(long tenantId, CallRecordQueryRequest request) {
        try {
            SearchRequest searchRequest = buildListSearchRequest(tenantId, request);
            SearchResponse<CallRecordSearchSource> response = elasticsearchClient.search(searchRequest, CallRecordSearchSource.class);

            List<CallRecordView> content = response.hits().hits().stream()
                    .map(hit -> toView(hit.source(), hit.highlight()))
                    .filter(view -> view != null)
                    .toList();
            long total = response.hits().total() == null ? content.size() : response.hits().total().value();
            return new PageResponse<>(content, request.getPage(), request.getSize(), total, "elasticsearch");
        } catch (IOException exception) {
            log.warn(
                    "event=call_record_query_failed tenantId={} phone={} reason={}",
                    tenantId,
                    maskPhone(request.getPhone()),
                    exception.getMessage()
            );
            throw new IllegalStateException("Failed to query call records from Elasticsearch", exception);
        }
    }

    public CallRecordDetailView detail(long tenantId, String callId) {
        try {
            GetResponse<CallRecordSearchSource> response = elasticsearchClient.get(get -> get
                            .index(CALL_RECORD_READ_ALIAS)
                            .id(callId)
                            .routing(Long.toString(tenantId)),
                    CallRecordSearchSource.class
            );
            if (!response.found() || response.source() == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Call record not found");
            }

            List<CallRoundView> rounds = searchRounds(tenantId, callId);
            return toDetailView(response.source(), rounds);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to query call record detail from Elasticsearch", exception);
        }
    }

    private SearchRequest buildListSearchRequest(long tenantId, CallRecordQueryRequest request) {
        SearchRequest.Builder builder = new SearchRequest.Builder()
                .index(CALL_RECORD_READ_ALIAS)
                .routing(Long.toString(tenantId))
                .from(request.getPage() * request.getSize())
                .size(request.getSize())
                .sort(sort -> sort.field(field -> field
                        .field(CallRecordSortField.from(request.getSortBy()).fieldName())
                        .order(toEsSortOrder(request.getSortOrder()))
                ))
                .query(buildQuery(tenantId, request));

        if (request.isHighlight() && StringUtils.hasText(request.getKeyword())) {
            builder.highlight(highlight -> highlight
                    .preTags("<em>")
                    .postTags("</em>")
                    .fields("full_text", field -> field)
            );
        }
        return builder.build();
    }

    private Query buildQuery(long tenantId, CallRecordQueryRequest request) {
        List<Query> filters = new ArrayList<>();
        filters.add(Query.of(query -> query.term(term -> term
                .field("tenant_id")
                .value(value -> value.longValue(tenantId))
        )));

        if (StringUtils.hasText(request.getPhone())) {
            filters.add(Query.of(query -> query.term(term -> term
                    .field("phone")
                    .value(value -> value.stringValue(request.getPhone()))
            )));
        }
        if (request.getCallStatus() != null) {
            filters.add(Query.of(query -> query.term(term -> term
                    .field("call_status")
                    .value(value -> value.longValue(request.getCallStatus().longValue()))
            )));
        }
        if (request.getStartTime() != null || request.getEndTime() != null) {
            filters.add(Query.of(query -> query.range(range -> {
                range.field("start_time");
                if (request.getStartTime() != null) {
                    range.gte(JsonData.of(request.getStartTime().toString()));
                }
                if (request.getEndTime() != null) {
                    range.lte(JsonData.of(request.getEndTime().toString()));
                }
                return range;
            })));
        }

        List<Query> must = new ArrayList<>();
        if (StringUtils.hasText(request.getKeyword())) {
            must.add(Query.of(query -> query.multiMatch(match -> match
                    .query(request.getKeyword())
                    .fields(List.of("full_text", "phone"))
            )));
        }

        return Query.of(query -> query.bool(bool -> {
            bool.filter(filters);
            if (!must.isEmpty()) {
                bool.must(must);
            }
            return bool;
        }));
    }

    private List<CallRoundView> searchRounds(long tenantId, String callId) throws IOException {
        SearchResponse<CallRoundSearchSource> response = elasticsearchClient.search(search -> search
                        .index(CALL_ROUND_READ_ALIAS)
                        .routing(Long.toString(tenantId))
                        .size(200)
                        .sort(sort -> sort.field(field -> field.field("round_index").order(SortOrder.Asc)))
                        .query(query -> query.bool(bool -> bool
                                .filter(Query.of(q -> q.term(term -> term
                                        .field("tenant_id")
                                        .value(value -> value.longValue(tenantId))
                                )))
                                .filter(Query.of(q -> q.term(term -> term
                                        .field("call_id")
                                        .value(value -> value.stringValue(callId))
                                )))
                        )),
                CallRoundSearchSource.class
        );
        return response.hits().hits().stream()
                .map(hit -> toRoundView(hit.source()))
                .filter(round -> round != null)
                .toList();
    }

    private CallRecordView toView(CallRecordSearchSource source, Map<String, List<String>> highlight) {
        if (source == null) {
            return null;
        }
        List<String> highlights = highlight == null ? Collections.emptyList() : highlight.getOrDefault("full_text", List.of());
        return new CallRecordView(
                source.getCallId(),
                source.getTenantId(),
                source.getTaskId(),
                source.getPhone(),
                source.getLineNumber(),
                source.getCallStatus(),
                source.getDuration(),
                source.getStartTime(),
                source.getEndTime(),
                source.getCreatedAt(),
                highlights
        );
    }

    private CallRecordDetailView toDetailView(CallRecordSearchSource source, List<CallRoundView> rounds) {
        return new CallRecordDetailView(
                source.getCallId(),
                source.getTenantId(),
                source.getTaskId(),
                source.getPhone(),
                source.getLineNumber(),
                source.getCallStatus(),
                source.getDuration(),
                source.getRecordingUrl(),
                source.getErrorCode(),
                source.getErrorDescription(),
                source.getHangupBy(),
                source.getConnected(),
                source.getRingDuration(),
                source.getRingStartTime(),
                source.getHangupTime(),
                source.getStartTime(),
                source.getEndTime(),
                source.getCreatedAt(),
                source.getFullText(),
                rounds
        );
    }

    private CallRoundView toRoundView(CallRoundSearchSource source) {
        if (source == null) {
            return null;
        }
        return new CallRoundView(
                source.getRoundIndex(),
                source.getSpeaker(),
                source.getContent(),
                source.getIntent(),
                source.getStartTime()
        );
    }

    private SortOrder toEsSortOrder(String sortOrder) {
        return SortOrderType.from(sortOrder) == SortOrderType.ASC ? SortOrder.Asc : SortOrder.Desc;
    }

    private String maskPhone(String phone) {
        if (!StringUtils.hasText(phone) || phone.length() < 7) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }
}
