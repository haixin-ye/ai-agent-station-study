package yhx.com.infrastructure.adapter.repository;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import yhx.com.domain.agent.adapter.repository.IVectorMemoryRepository;
import yhx.com.domain.agent.model.valobj.enums.memory.VectorCollectionTypeEnumVO;
import yhx.com.domain.agent.model.valobj.enums.memory.VectorSourceTypeEnumVO;
import yhx.com.domain.agent.model.valobj.memory.VectorIndexRecordVO;
import yhx.com.domain.agent.model.valobj.memory.VectorRecallFilterVO;
import yhx.com.domain.agent.model.valobj.memory.VectorRecallHitVO;
import yhx.com.domain.agent.model.valobj.memory.VectorRecallQueryVO;
import yhx.com.domain.agent.model.valobj.memory.VectorStoredRecordVO;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Repository
@ConditionalOnBean(name = {"pgVectorJdbcTemplate", "autoAgentEmbeddingModel"})
public class PgVectorMemoryRepository implements IVectorMemoryRepository {

    private static final int DEFAULT_TOP_K = 8;
    private static final double DEFAULT_MIN_SCORE = 0.3D;
    private static final Pattern DISTINCTIVE_QUERY_TOKEN_PATTERN = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_.:-]{1,}");

    private final JdbcTemplate jdbcTemplate;
    private final EmbeddingModel embeddingModel;

    public PgVectorMemoryRepository(@Qualifier("pgVectorJdbcTemplate") JdbcTemplate jdbcTemplate,
                                    @Qualifier("autoAgentEmbeddingModel") EmbeddingModel embeddingModel) {
        this.jdbcTemplate = jdbcTemplate;
        this.embeddingModel = embeddingModel;
    }

    @Override
    public String upsert(VectorIndexRecordVO record) {
        validateRecord(record);
        String vectorId = firstNonBlank(record.getVectorId(), UUID.randomUUID().toString());
        String tableName = tableName(record.getCollectionType());
        String text = firstNonBlank(record.getText(), record.getSummary());
        String embedding = vectorLiteral(embeddingModel.embed(text));
        String metadataJson = metadataJson(record);
        return jdbcTemplate.queryForObject("""
                        INSERT INTO public.%s (
                            id, source_type, source_id, user_id, session_id, content, summary,
                            metadata, occurred_at, embedding
                        ) VALUES (
                            ?::uuid, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?::vector
                        )
                        ON CONFLICT (source_type, source_id) DO UPDATE SET
                            content = EXCLUDED.content,
                            summary = EXCLUDED.summary,
                            metadata = EXCLUDED.metadata,
                            occurred_at = EXCLUDED.occurred_at,
                            embedding = EXCLUDED.embedding
                        RETURNING id::text
                        """.formatted(tableName),
                String.class,
                vectorId,
                record.getSourceType().name(),
                record.getSourceId(),
                record.getUserId(),
                record.getSessionId(),
                text,
                record.getSummary(),
                metadataJson,
                toTimestamp(record.getOccurredAt()),
                embedding);
    }

    @Override
    public List<VectorRecallHitVO> search(VectorRecallQueryVO query) {
        validateQuery(query);
        String embedding = vectorLiteral(embeddingModel.embed(query.getQueryText()));
        VectorRecallFilterVO filter = query.getFilter();
        List<VectorCollectionTypeEnumVO> collections = collections(filter);
        int topK = query.getTopK() == null || query.getTopK() <= 0 ? DEFAULT_TOP_K : query.getTopK();
        double minScore = query.getMinScore() == null ? DEFAULT_MIN_SCORE : query.getMinScore();
        List<VectorRecallHitVO> hits = new ArrayList<>();
        for (VectorCollectionTypeEnumVO collection : collections) {
            hits.addAll(searchCollection(collection, embedding, filter, topK, minScore));
        }
        return hits.stream()
                .sorted(Comparator.comparing(VectorRecallHitVO::getScore, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(topK)
                .toList();
    }

    @Override
    public List<VectorRecallHitVO> lexicalSearch(VectorRecallQueryVO query) {
        validateQuery(query);
        VectorRecallFilterVO filter = query.getFilter();
        List<String> terms = distinctiveTerms(query.getQueryText());
        if (terms.isEmpty()) {
            return List.of();
        }
        List<VectorCollectionTypeEnumVO> collections = collections(filter);
        int topK = query.getTopK() == null || query.getTopK() <= 0 ? DEFAULT_TOP_K : query.getTopK();
        List<VectorRecallHitVO> hits = new ArrayList<>();
        for (VectorCollectionTypeEnumVO collection : collections) {
            hits.addAll(lexicalSearchCollection(collection, terms, filter, topK));
        }
        return hits.stream()
                .sorted(Comparator.comparing(VectorRecallHitVO::getScore, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(topK)
                .toList();
    }

    @Override
    public List<VectorStoredRecordVO> listStoredRecords(List<VectorCollectionTypeEnumVO> collectionTypes,
                                                        Map<String, Object> metadataFilters,
                                                        int limit) {
        if (collectionTypes == null || collectionTypes.isEmpty()) {
            return List.of();
        }
        int boundedLimit = Math.min(5000, Math.max(1, limit));
        List<VectorStoredRecordVO> records = new ArrayList<>();
        for (VectorCollectionTypeEnumVO collectionType : collectionTypes) {
            StringBuilder sql = new StringBuilder("""
                    SELECT id::text AS vector_id,
                           source_type,
                           source_id,
                           user_id,
                           session_id,
                           content,
                           summary,
                           metadata::text AS metadata,
                           occurred_at,
                           vector_dims(embedding) AS embedding_dimensions
                    FROM public.%s
                    WHERE 1 = 1
                    """.formatted(tableName(collectionType)));
            List<Object> args = new ArrayList<>();
            if (metadataFilters != null && !metadataFilters.isEmpty()) {
                sql.append(" AND metadata @> ?::jsonb");
                args.add(JSON.toJSONString(metadataFilters));
            }
            sql.append(" ORDER BY occurred_at DESC NULLS LAST, source_id LIMIT ?");
            args.add(boundedLimit);
            records.addAll(jdbcTemplate.query(sql.toString(),
                    (rs, rowNum) -> VectorStoredRecordVO.builder()
                            .collectionType(collectionType)
                            .vectorId(rs.getString("vector_id"))
                            .sourceType(VectorSourceTypeEnumVO.valueOf(rs.getString("source_type")))
                            .sourceId(rs.getString("source_id"))
                            .userId(rs.getString("user_id"))
                            .sessionId(rs.getString("session_id"))
                            .content(rs.getString("content"))
                            .summary(rs.getString("summary"))
                            .metadata(parseMetadata(rs.getString("metadata")))
                            .embeddingDimensions((Integer) rs.getObject("embedding_dimensions"))
                            .occurredAt(toLocalDateTime(rs.getTimestamp("occurred_at")))
                            .build(), args.toArray()));
        }
        return records.stream()
                .sorted(Comparator.comparing(VectorStoredRecordVO::getOccurredAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(boundedLimit)
                .toList();
    }

    @Override
    public int mergeMetadata(VectorCollectionTypeEnumVO collectionType,
                             String sourceId,
                             Map<String, Object> metadata) {
        if (collectionType == null || isBlank(sourceId) || metadata == null || metadata.isEmpty()) {
            return 0;
        }
        return jdbcTemplate.update("""
                        UPDATE public.%s
                        SET metadata = COALESCE(metadata, '{}'::jsonb) || ?::jsonb
                        WHERE source_id = ?
                        """.formatted(tableName(collectionType)),
                JSON.toJSONString(metadata), sourceId);
    }

    @Override
    public void disable(VectorCollectionTypeEnumVO collectionType, String sourceId) {
        if (collectionType == null || isBlank(sourceId)) {
            return;
        }
        jdbcTemplate.update("DELETE FROM public.%s WHERE source_id = ?".formatted(tableName(collectionType)), sourceId);
    }

    private List<VectorRecallHitVO> searchCollection(VectorCollectionTypeEnumVO collection,
                                                     String embedding,
                                                     VectorRecallFilterVO filter,
                                                     int topK,
                                                     double minScore) {
        StringBuilder sql = new StringBuilder("""
                SELECT id::text AS vector_id,
                       source_type,
                       source_id,
                       user_id,
                       session_id,
                       content,
                       summary,
                       metadata::text AS metadata,
                       occurred_at,
                       1 - (embedding <=> ?::vector) AS score
                FROM public.%s
                WHERE embedding IS NOT NULL
                """.formatted(tableName(collection)));
        List<Object> args = new ArrayList<>();
        args.add(embedding);
        if (filter != null && !isBlank(filter.getUserId())) {
            sql.append(" AND (user_id = ? OR user_id IS NULL OR user_id = '')");
            args.add(filter.getUserId());
        }
        if (filter != null && !isBlank(filter.getSessionId())) {
            sql.append(" AND (session_id = ? OR session_id IS NULL OR session_id = '')");
            args.add(filter.getSessionId());
        }
        if (filter != null && filter.getFrom() != null) {
            sql.append(" AND (occurred_at IS NULL OR occurred_at >= ?)");
            args.add(toTimestamp(filter.getFrom()));
        }
        if (filter != null && filter.getTo() != null) {
            sql.append(" AND (occurred_at IS NULL OR occurred_at <= ?)");
            args.add(toTimestamp(filter.getTo()));
        }
        appendMetadataFilter(sql, args, filter);
        sql.append(" ORDER BY embedding <=> ?::vector LIMIT ?");
        args.add(embedding);
        args.add(topK);

        return jdbcTemplate.query(sql.toString(), this::mapHit, args.toArray()).stream()
                .filter(hit -> hit.getScore() == null || hit.getScore() >= minScore)
                .map(hit -> {
                    hit.setCollectionType(collection);
                    return hit;
                })
                .toList();
    }

    private List<VectorRecallHitVO> lexicalSearchCollection(VectorCollectionTypeEnumVO collection,
                                                            List<String> terms,
                                                            VectorRecallFilterVO filter,
                                                            int topK) {
        StringBuilder sql = new StringBuilder("""
                SELECT id::text AS vector_id,
                       source_type,
                       source_id,
                       user_id,
                       session_id,
                       content,
                       summary,
                       metadata::text AS metadata,
                       occurred_at,
                       1.0 AS score
                FROM public.%s
                WHERE 1 = 1
                """.formatted(tableName(collection)));
        List<Object> args = new ArrayList<>();
        appendScopeFilter(sql, args, filter);
        sql.append(" AND (");
        for (int i = 0; i < terms.size(); i++) {
            if (i > 0) {
                sql.append(" OR ");
            }
            sql.append("content ILIKE ? OR summary ILIKE ? OR metadata::text ILIKE ?");
            String pattern = "%" + escapeLike(terms.get(i)) + "%";
            args.add(pattern);
            args.add(pattern);
            args.add(pattern);
        }
        sql.append(") ORDER BY occurred_at DESC NULLS LAST LIMIT ?");
        args.add(topK);

        return jdbcTemplate.query(sql.toString(), this::mapHit, args.toArray()).stream()
                .map(hit -> {
                    hit.setCollectionType(collection);
                    return hit;
                })
                .toList();
    }

    private void appendScopeFilter(StringBuilder sql, List<Object> args, VectorRecallFilterVO filter) {
        if (filter != null && !isBlank(filter.getUserId())) {
            sql.append(" AND (user_id = ? OR user_id IS NULL OR user_id = '')");
            args.add(filter.getUserId());
        }
        if (filter != null && !isBlank(filter.getSessionId())) {
            sql.append(" AND (session_id = ? OR session_id IS NULL OR session_id = '')");
            args.add(filter.getSessionId());
        }
        if (filter != null && filter.getFrom() != null) {
            sql.append(" AND (occurred_at IS NULL OR occurred_at >= ?)");
            args.add(toTimestamp(filter.getFrom()));
        }
        if (filter != null && filter.getTo() != null) {
            sql.append(" AND (occurred_at IS NULL OR occurred_at <= ?)");
            args.add(toTimestamp(filter.getTo()));
        }
        appendMetadataFilter(sql, args, filter);
    }

    private void appendMetadataFilter(StringBuilder sql, List<Object> args, VectorRecallFilterVO filter) {
        if (filter == null || filter.getMetadataFilters() == null || filter.getMetadataFilters().isEmpty()) {
            return;
        }
        sql.append(" AND metadata @> ?::jsonb");
        args.add(JSON.toJSONString(filter.getMetadataFilters()));
    }

    private VectorRecallHitVO mapHit(ResultSet rs, int rowNum) throws SQLException {
        return VectorRecallHitVO.builder()
                .vectorId(rs.getString("vector_id"))
                .sourceType(VectorSourceTypeEnumVO.valueOf(rs.getString("source_type")))
                .sourceId(rs.getString("source_id"))
                .score(rs.getDouble("score"))
                .summary(rs.getString("summary"))
                .snippet(rs.getString("content"))
                .metadata(parseMetadata(rs.getString("metadata")))
                .occurredAt(toLocalDateTime(rs.getTimestamp("occurred_at")))
                .build();
    }

    private String tableName(VectorCollectionTypeEnumVO collectionType) {
        if (collectionType == null) {
            throw new IllegalArgumentException("Vector collection type is required.");
        }
        return collectionType.collectionName();
    }

    private List<VectorCollectionTypeEnumVO> collections(VectorRecallFilterVO filter) {
        if (filter == null || filter.getCollectionTypes() == null || filter.getCollectionTypes().isEmpty()) {
            return List.of(VectorCollectionTypeEnumVO.values());
        }
        return filter.getCollectionTypes();
    }

    private String metadataJson(VectorIndexRecordVO record) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (record.getMetadata() != null) {
            metadata.putAll(record.getMetadata());
        }
        metadata.put("collectionType", record.getCollectionType().name());
        metadata.put("sourceType", record.getSourceType().name());
        metadata.put("sourceId", record.getSourceId());
        if (!isBlank(record.getContentHash())) {
            metadata.put("contentHash", record.getContentHash());
        }
        return JSON.toJSONString(metadata);
    }

    private Map<String, Object> parseMetadata(String metadataJson) {
        if (isBlank(metadataJson)) {
            return Map.of();
        }
        try {
            JSONObject object = JSON.parseObject(metadataJson);
            return object == null ? Map.of() : object.getInnerMap();
        } catch (Exception e) {
            log.warn("Failed to parse vector metadata json, metadata={}", metadataJson);
            return Map.of();
        }
    }

    private String vectorLiteral(float[] embedding) {
        if (embedding == null || embedding.length == 0) {
            throw new IllegalStateException("Embedding model returned empty vector.");
        }
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(embedding[i]);
        }
        return builder.append(']').toString();
    }

    private List<String> distinctiveTerms(String queryText) {
        if (isBlank(queryText)) {
            return List.of();
        }
        Map<String, String> terms = new LinkedHashMap<>();
        Matcher matcher = DISTINCTIVE_QUERY_TOKEN_PATTERN.matcher(queryText);
        while (matcher.find()) {
            String term = matcher.group();
            if (term.length() >= 2) {
                terms.put(term.toLowerCase(), term);
            }
        }
        return new ArrayList<>(terms.values());
    }

    private String escapeLike(String value) {
        return value == null ? "" : value
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }

    private void validateRecord(VectorIndexRecordVO record) {
        if (record == null) {
            throw new IllegalArgumentException("Vector index record is required.");
        }
        if (record.getCollectionType() == null) {
            throw new IllegalArgumentException("Vector collection type is required.");
        }
        if (record.getSourceType() == null) {
            throw new IllegalArgumentException("Vector source type is required.");
        }
        if (isBlank(record.getSourceId())) {
            throw new IllegalArgumentException("Vector source id is required.");
        }
        if (isBlank(firstNonBlank(record.getText(), record.getSummary()))) {
            throw new IllegalArgumentException("Vector index text is required.");
        }
    }

    private void validateQuery(VectorRecallQueryVO query) {
        if (query == null || isBlank(query.getQueryText())) {
            throw new IllegalArgumentException("Vector recall query text is required.");
        }
    }

    private Timestamp toTimestamp(LocalDateTime value) {
        return value == null ? null : Timestamp.valueOf(value);
    }

    private LocalDateTime toLocalDateTime(Timestamp value) {
        return value == null ? null : value.toLocalDateTime();
    }

    private String firstNonBlank(String first, String second) {
        return isBlank(first) ? second : first;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
