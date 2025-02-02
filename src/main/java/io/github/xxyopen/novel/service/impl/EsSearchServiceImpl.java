package io.github.xxyopen.novel.service.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.MatchQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.RangeQuery;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.TotalHits;
import co.elastic.clients.json.JsonData;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import io.github.xxyopen.novel.core.common.resp.PageRespDto;
import io.github.xxyopen.novel.core.common.resp.RestResp;
import io.github.xxyopen.novel.core.constant.EsConsts;
import io.github.xxyopen.novel.dto.es.EsBookDto;
import io.github.xxyopen.novel.dto.req.BookSearchReqDto;
import io.github.xxyopen.novel.dto.resp.BookInfoRespDto;
import io.github.xxyopen.novel.service.SearchService;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Elasticsearch 搜索 服务实现类
 *
 * @author xiongxiaoyang
 * @date 2022/5/23
 */
@ConditionalOnProperty(prefix = "spring.elasticsearch", name = "enable", havingValue = "true")
@Service
@RequiredArgsConstructor
@Slf4j
public class EsSearchServiceImpl implements SearchService {

    private final ElasticsearchClient esClient;

    @SneakyThrows
    @Override
    public RestResp<PageRespDto<BookInfoRespDto>> searchBooks(BookSearchReqDto condition) {

        SearchResponse<EsBookDto> response = esClient.search(s -> {

                    SearchRequest.Builder searchBuilder = s.index(EsConsts.IndexEnum.BOOK.getName());
                    buildSearchCondition(condition, searchBuilder);

                    searchBuilder.sort(o ->
                                    o.field(f -> f.field(StringUtils
                                                    .underlineToCamel(condition.getSort().split(" ")[0]))
                                            .order(SortOrder.Desc))
                            )
                            .from((condition.getPageNum() - 1) * condition.getPageSize())
                            .size(condition.getPageSize());
                    return searchBuilder;
                },
                EsBookDto.class
        );

        TotalHits total = response.hits().total();

        List<BookInfoRespDto> list = new ArrayList<>();
        List<Hit<EsBookDto>> hits = response.hits().hits();
        for (Hit<EsBookDto> hit : hits) {
            EsBookDto book = hit.source();
            assert book != null;
            list.add(BookInfoRespDto.builder()
                    .id(book.getId())
                    .bookName(book.getBookName())
                    .categoryId(book.getCategoryId())
                    .categoryName(book.getCategoryName())
                    .authorId(book.getAuthorId())
                    .authorName(book.getAuthorName())
                    .wordCount(book.getWordCount())
                    .lastChapterName(book.getLastChapterName())
                    .build());
        }
        assert total != null;
        return RestResp.ok(PageRespDto.of(condition.getPageNum(), condition.getPageSize(), total.value(), list));
    }

    private void buildSearchCondition(BookSearchReqDto condition, SearchRequest.Builder searchBuilder) {
        if (!StringUtils.isBlank(condition.getKeyword())) {
            searchBuilder.query(q -> q.match(t -> t
                                    .field("bookName")
                                    .query(condition.getKeyword())
                                    .boost(2.0f)
                                    .field("authorName")
                                    .query(condition.getKeyword())
                                    .boost(1.8f)
                            //.field("categoryName")
                            //.query(condition.getKeyword())
                            //.boost(1.0f)
                            //.field("bookDesc")
                            //.query(condition.getKeyword())
                            //.boost(0.1f)
                    )
            );
        }

        if (Objects.nonNull(condition.getWorkDirection())) {
            searchBuilder.query(MatchQuery.of(m -> m
                    .field("workDirection")
                    .query(condition.getWorkDirection())
            )._toQuery());
        }

        if (Objects.nonNull(condition.getCategoryId())) {
            searchBuilder.query(MatchQuery.of(m -> m
                    .field("categoryId")
                    .query(condition.getCategoryId())
            )._toQuery());
        }

        if (Objects.nonNull(condition.getWordCountMin())) {
            searchBuilder.query(RangeQuery.of(m -> m
                    .field("wordCount")
                    .gte(JsonData.of(condition.getWordCountMin()))
            )._toQuery());
        }

        if (Objects.nonNull(condition.getWordCountMax())) {
            searchBuilder.query(RangeQuery.of(m -> m
                    .field("wordCount")
                    .lt(JsonData.of(condition.getWordCountMax()))
            )._toQuery());
        }

        if (Objects.nonNull(condition.getUpdateTimeMin())) {
            searchBuilder.query(RangeQuery.of(m -> m
                    .field("lastChapterUpdateTime")
                    .gte(JsonData.of(condition.getUpdateTimeMin().getTime()))
            )._toQuery());
        }
    }
}
