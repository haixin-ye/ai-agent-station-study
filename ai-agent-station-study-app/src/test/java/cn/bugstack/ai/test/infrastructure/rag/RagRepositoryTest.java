package cn.bugstack.ai.test.infrastructure.rag;

import cn.bugstack.ai.domain.agent.model.entity.RagFileIngestCommandEntity;
import cn.bugstack.ai.infrastructure.adapter.repository.RagRepository;
import org.junit.Assert;
import org.junit.Test;
import org.redisson.api.RKeys;
import org.redisson.api.RList;
import org.redisson.api.RSet;
import org.redisson.api.RType;
import org.redisson.api.RedissonClient;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;

import static org.mockito.Mockito.*;

public class RagRepositoryTest {

    @Test
    public void test_queryRagTagList_wrongTypeKey_shouldDeleteAndReturnSet() {
        RedissonClient redissonClient = mock(RedissonClient.class);
        RKeys keys = mock(RKeys.class);
        @SuppressWarnings("unchecked")
        RSet<String> tagSet = mock(RSet.class);

        when(redissonClient.getKeys()).thenReturn(keys);
        when(keys.getType("ragTag")).thenReturn(RType.STRING);
        when(redissonClient.getSet("ragTag")).thenReturn(tagSet);
        when(tagSet.iterator()).thenReturn(Collections.emptyIterator());

        RagRepository ragRepository = new RagRepository();
        ReflectionTestUtils.setField(ragRepository, "redissonClient", redissonClient);

        Set<String> result = ragRepository.queryRagTagList();

        Assert.assertTrue(result.isEmpty());
        verify(keys, times(1)).delete("ragTag");
    }

    @Test
    public void test_ingestFiles_shouldAppendTagToSet() {
        RedissonClient redissonClient = mock(RedissonClient.class);
        RKeys keys = mock(RKeys.class);
        @SuppressWarnings("unchecked")
        RSet<String> tagSet = mock(RSet.class);

        when(redissonClient.getKeys()).thenReturn(keys);
        when(keys.getType("ragTag")).thenReturn(RType.SET);
        when(redissonClient.getSet("ragTag")).thenReturn(tagSet);
        when(tagSet.contains("agent-docs")).thenReturn(false);

        RagRepository ragRepository = new RagRepository();
        ReflectionTestUtils.setField(ragRepository, "redissonClient", redissonClient);

        ragRepository.ingestFiles(RagFileIngestCommandEntity.builder()
                .knowledgeTag("agent-docs")
                .files(Collections.emptyList())
                .build());

        verify(tagSet, times(1)).add("agent-docs");
        verify(keys, never()).delete("ragTag");
    }

    @Test
    public void test_queryRagTagList_legacyList_shouldMigrateToSet() {
        RedissonClient redissonClient = mock(RedissonClient.class);
        RKeys keys = mock(RKeys.class);
        @SuppressWarnings("unchecked")
        RList<String> legacyTagList = mock(RList.class);
        @SuppressWarnings("unchecked")
        RSet<String> tagSet = mock(RSet.class);

        when(redissonClient.getKeys()).thenReturn(keys);
        when(keys.getType("ragTag")).thenReturn(RType.LIST);
        when(redissonClient.getList("ragTag")).thenReturn(legacyTagList);
        when(legacyTagList.readAll()).thenReturn(Arrays.asList("tag-a", "tag-b", "tag-a"));
        when(redissonClient.getSet("ragTag")).thenReturn(tagSet);
        when(tagSet.iterator()).thenReturn(Collections.emptyIterator());

        RagRepository ragRepository = new RagRepository();
        ReflectionTestUtils.setField(ragRepository, "redissonClient", redissonClient);

        ragRepository.queryRagTagList();

        verify(keys, times(1)).delete("ragTag");
        verify(tagSet, times(1)).addAll(new java.util.LinkedHashSet<>(Arrays.asList("tag-a", "tag-b")));
    }
}
