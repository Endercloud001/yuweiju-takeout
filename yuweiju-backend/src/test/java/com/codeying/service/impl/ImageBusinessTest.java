package com.codeying.service.impl;

import com.aliyun.oss.model.OSSObjectSummary;
import com.codeying.dto.admin.common.UploadFileDTO;
import com.codeying.exception.BusinessException;
import com.codeying.properties.AliOssProperties;
import com.codeying.utils.AliOssUtil;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import java.util.List;
import java.util.function.Predicate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ImageBusinessTest {
    AliOssUtil oss = mock(AliOssUtil.class);
    UploadServiceImpl uploads = new UploadServiceImpl(oss);
    UploadFileDTO file(String name, byte[] bytes) {
        var dto = new UploadFileDTO(); dto.setFile(new MockMultipartFile("file", name, "image/png", bytes)); return dto;
    }
    void rejects(UploadFileDTO dto, String message) {
        assertEquals(message, assertThrows(BusinessException.class, () -> uploads.upload(dto)).getMessage());
        verifyNoInteractions(oss);
    }
    @Test void missingEmptyFilenameAndFormat() {
        rejects(null, "文件为空"); rejects(new UploadFileDTO(), "文件为空");
        rejects(file("x.png", new byte[0]), "文件为空");
        rejects(file(" ", new byte[]{1}), "文件名为空");
        for (String name : List.of("x", "x.", "x.gif", "x.webp", "x.exe")) rejects(file(name, new byte[]{1}), "文件格式错误");
    }
    @Test void allowedFormatsAndPathRules() {
        when(oss.upload(any(java.io.InputStream.class), anyString())).thenReturn("local-url");
        for (String ext : List.of("png", "jpg", "jpeg", "bmp", "PNG")) {
            var dto = file("photo." + ext, new byte[]{1}); dto.setScene("/dish/../"); dto.setDir("/folder\\./month/");
            assertEquals("local-url", uploads.upload(dto));
            verify(oss).upload(any(java.io.InputStream.class), matches("dish/folder/month/photo_[0-9a-f-]+\\." + ext.toLowerCase()));
            clearInvocations(oss);
        }
        var dto = file("photo.png", new byte[]{1}); dto.setDir("../.");
        uploads.upload(dto);
        verify(oss).upload(any(java.io.InputStream.class), matches("[0-9]{4}/[0-9]{2}/photo_[0-9a-f-]+\\.png"));
    }
    @Test void defaultCustomAndHardSizeBoundaries() {
        when(oss.upload(any(java.io.InputStream.class),anyString())).thenReturn("url");
        assertEquals("url", uploads.upload(file("x.png", new byte[5*1024*1024])));
        var tooLarge=file("x.png",new byte[5*1024*1024+1]);
        assertEquals("文件过大",assertThrows(BusinessException.class,()->uploads.upload(tooLarge)).getMessage());
        var custom=file("x.png",new byte[3]); custom.setMaxSize(2L);
        assertThrows(BusinessException.class,()->uploads.upload(custom)); custom.setMaxSize(3L); assertEquals("url",uploads.upload(custom));
        var hard=file("x.png",new byte[20*1024*1024+1]); hard.setMaxSize(Long.MAX_VALUE);
        assertEquals("文件过大",assertThrows(BusinessException.class,()->uploads.upload(hard)).getMessage());
        var atHard=file("x.png",new byte[20*1024*1024]); atHard.setMaxSize(Long.MAX_VALUE);
        assertEquals("url",uploads.upload(atHard));
    }
    @Test void upstreamAndReadFailureKeepCauseAndFriendlyMessage() throws Exception {
        var cause = new IllegalStateException("synthetic-third-party-secret-body?Signature=synthetic");
        when(oss.upload(any(java.io.InputStream.class),anyString())).thenThrow(cause);
        var failure=assertThrows(BusinessException.class,()->uploads.upload(file("x.png",new byte[]{1})));
        assertSame(cause,failure.getCause()); assertEquals("上传失败",failure.getMessage());
        var dto=new UploadFileDTO(); var multipart=mock(org.springframework.web.multipart.MultipartFile.class);
        dto.setFile(multipart); when(multipart.getOriginalFilename()).thenReturn("x.jpg"); when(multipart.getSize()).thenReturn(1L);
        var read=new java.io.IOException("synthetic-read-secret"); when(multipart.getInputStream()).thenThrow(read);
        assertSame(read,assertThrows(BusinessException.class,()->uploads.upload(dto)).getCause());
    }
    @Test void streamsCloseOnSuccessAndStorageFailure() throws Exception {
        for (boolean fail : List.of(false,true)) {
            var closed=new java.util.concurrent.atomic.AtomicBoolean();
            var input=new java.io.ByteArrayInputStream(new byte[]{1}) {
                @Override public void close() throws java.io.IOException { closed.set(true); super.close(); }
            };
            var multipart=mock(org.springframework.web.multipart.MultipartFile.class);
            when(multipart.getOriginalFilename()).thenReturn("x.png"); when(multipart.getInputStream()).thenReturn(input);
            var dto=new UploadFileDTO(); dto.setFile(multipart);
            if (fail) when(oss.upload(any(java.io.InputStream.class),anyString())).thenThrow(new IllegalStateException("synthetic"));
            else when(oss.upload(any(java.io.InputStream.class),anyString())).thenReturn("url");
            if (fail) assertThrows(BusinessException.class,()->uploads.upload(dto)); else assertEquals("url",uploads.upload(dto));
            assertTrue(closed.get()); reset(oss);
        }
    }
    static OSSObjectSummary summary(String key) { var s=new OSSObjectSummary(); s.setKey(key); s.setSize(17); return s; }
    @Test void configuredScenesLimitsAndExtensionPolicy() {
        var props=new AliOssProperties(); props.setDefaultImagePrefix(" /general "); props.setDishDefaultImagePrefix("\\dish");
        props.setSetmealDefaultImagePrefix("meal/"); props.setMaxListCount(3); props.setAllowedExtensions(List.of(" PNG ","webp"));
        when(oss.listObjects(anyString(),anyInt(),any())).thenAnswer(invocation -> {
            Predicate<OSSObjectSummary> filter=invocation.getArgument(2);
            return java.util.stream.Stream.of(summary("dir/"),summary("a.txt"),summary("a.bmp"),summary("a.PNG"),summary("b.webp"))
                    .filter(filter).limit(invocation.getArgument(1,Integer.class)).toList();
        });
        when(oss.buildObjectUrl(anyString())).thenAnswer(i->"local/"+i.getArgument(0));
        var library=new DefaultImageLibraryServiceImpl(oss,props);
        var images=library.listDefaultImages(" DISH ",99); assertEquals(2,images.size());
        assertEquals("a.PNG",images.get(0).getName()); assertEquals("dish",images.get(0).getScene());
        assertEquals(17L,images.get(0).getSize()); assertNull(images.get(0).getLastModified());
        verify(oss).listObjects(eq("dish/"),eq(3),any());
        assertEquals(1,library.listDefaultImages("setmeal",1).size()); verify(oss).listObjects(eq("meal/"),eq(1),any());
        library.listDefaultImages(null,null); verify(oss).listObjects(eq("general/"),eq(3),any());
        library.listDefaultImages("unknown",-1); verify(oss,times(2)).listObjects(eq("general/"),eq(3),any());
        props.setDefaultImagePrefix(""); library.listDefaultImages(null,1); verify(oss).listObjects(eq(""),eq(1),any());
    }
    @Test void emptyAndUpstreamLibraryFailure() {
        var library=new DefaultImageLibraryServiceImpl(oss,new AliOssProperties());
        when(oss.listObjects(anyString(),anyInt(),any())).thenReturn(List.of());
        assertTrue(library.listDefaultImages(null,1).isEmpty());
        var cause=new IllegalStateException("synthetic-secret"); when(oss.listObjects(anyString(),anyInt(),any())).thenThrow(cause);
        var failure=assertThrows(BusinessException.class,()->library.listDefaultImages("dish",1));
        assertSame(cause,failure.getCause()); assertEquals("获取默认图片失败",failure.getMessage());
    }
}
