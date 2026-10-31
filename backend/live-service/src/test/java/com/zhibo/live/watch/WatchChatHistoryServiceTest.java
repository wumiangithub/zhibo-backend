package com.zhibo.live.watch;

import com.zhibo.activity.ActivityEntity;
import com.zhibo.live.watch.dto.WatchChatHistoryResponse;
import com.zhibo.liveprovider.LiveProvider;
import com.zhibo.liveprovider.LiveProviderRegistry;
import com.zhibo.liveprovider.ProviderNotAvailableException;
import com.zhibo.liveprovider.dto.ProviderChatHistory;
import com.zhibo.liveprovider.dto.ProviderChatMessage;
import com.zhibo.vhall.exception.VhallException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WatchChatHistoryServiceTest {

    private static final String ID = "a1b2c3d4e5f67890abcdef1234567890";

    @Mock
    private WatchService watchService;
    @Mock
    private LiveProviderRegistry providerRegistry;
    @Mock
    private LiveProvider liveProvider;

    @InjectMocks
    private WatchChatHistoryService service;

    @Test
    void list_reversesNewestFirstToOldestFirst() {
        ActivityEntity entity = activity();
        when(watchService.requireActivity(ID)).thenReturn(entity);
        when(providerRegistry.require("vhall")).thenReturn(liveProvider);
        when(liveProvider.listChatHistory("1001", 0, 50)).thenReturn(new ProviderChatHistory(
                2L,
                List.of(
                        new ProviderChatMessage("u2", "乙", "后说", "m2", null, 2, 2000L),
                        new ProviderChatMessage("u1", "甲", "先说", "m1", null, 2, 1000L))));

        WatchChatHistoryResponse res = service.list(ID, 1);
        assertEquals(2, res.getTotal());
        assertEquals(1, res.getPage());
        assertEquals(50, res.getPageSize());
        assertEquals(2, res.getList().size());
        assertEquals("先说", res.getList().get(0).getMsg());
        assertEquals("后说", res.getList().get(1).getMsg());
        verify(liveProvider).listChatHistory(eq("1001"), eq(0), eq(50));
    }

    @Test
    void list_page2_usesPos50() {
        ActivityEntity entity = activity();
        when(watchService.requireActivity(ID)).thenReturn(entity);
        when(providerRegistry.require("vhall")).thenReturn(liveProvider);
        when(liveProvider.listChatHistory("1001", 50, 50))
                .thenReturn(new ProviderChatHistory(60L, List.of()));

        WatchChatHistoryResponse res = service.list(ID, 2);
        assertEquals(60, res.getTotal());
        assertEquals(2, res.getPage());
        assertTrue(res.getList().isEmpty());
        verify(liveProvider).listChatHistory("1001", 50, 50);
    }

    @Test
    void list_vhallFailure_returnsEmpty() {
        ActivityEntity entity = activity();
        when(watchService.requireActivity(ID)).thenReturn(entity);
        when(providerRegistry.require("vhall")).thenReturn(liveProvider);
        when(liveProvider.listChatHistory("1001", 0, 50))
                .thenThrow(new VhallException("down"));

        WatchChatHistoryResponse res = service.list(ID, 1);
        assertEquals(0, res.getTotal());
        assertTrue(res.getList().isEmpty());
    }

    @Test
    void list_unsupportedProvider_throws501() {
        ActivityEntity entity = activity();
        entity.setProvider("aliyun");
        when(watchService.requireActivity(ID)).thenReturn(entity);
        when(providerRegistry.require("aliyun")).thenReturn(liveProvider);
        when(liveProvider.listChatHistory("1001", 0, 50))
                .thenThrow(new UnsupportedOperationException("未接入"));

        assertThrows(ProviderNotAvailableException.class, () -> service.list(ID, 1));
    }

    @Test
    void list_pageLessThanOne_throws() {
        assertThrows(IllegalArgumentException.class, () -> service.list(ID, 0));
    }

    private static ActivityEntity activity() {
        ActivityEntity entity = new ActivityEntity();
        entity.setId(ID);
        entity.setProvider("vhall");
        entity.setProviderRoomId("1001");
        entity.setReady(1);
        return entity;
    }
}
