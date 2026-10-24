package com.kuoge.agentos.business.ads;

import com.kuoge.agentos.skill.ad.ItemAssembleService;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 母集合就绪状态内存实现（一期 mock）：未知集合一律视为未就绪，
 * 由「母集合就绪」入口（ready 端点 / 事件）标记后放行组装。
 */
public class InMemoryCollectionReadiness implements ItemAssembleService.CollectionReadiness {

    private final Set<String> readyCollections = ConcurrentHashMap.newKeySet();

    @Override
    public boolean ready(String collectionId) {
        return collectionId != null && readyCollections.contains(collectionId);
    }

    /** 标记母集合就绪（方案 3.7 母链接就绪事件） */
    public void markReady(String collectionId) {
        readyCollections.add(collectionId);
    }
}
