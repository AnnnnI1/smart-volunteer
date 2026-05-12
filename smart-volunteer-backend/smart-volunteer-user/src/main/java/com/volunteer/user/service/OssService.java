package com.volunteer.user.service;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.ObjectMetadata;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
public class OssService {

    @Value("${aliyun.oss.endpoint}")
    private String endpoint;

    @Value("${aliyun.oss.access-key-id}")
    private String accessKeyId;

    @Value("${aliyun.oss.access-key-secret}")
    private String accessKeySecret;

    @Value("${aliyun.oss.bucket-name}")
    private String bucketName;

    private static final List<String> ALLOWED_TYPES =
            Arrays.asList("image/jpeg", "image/png", "image/gif", "image/webp");
    private static final long MAX_SIZE = 5 * 1024 * 1024L; // 5MB

    /**
     * 上传头像到 OSS，返回公网可访问 URL
     */
    public String uploadAvatar(MultipartFile file, Long userId) throws IOException {
        // 文件类型校验
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType)) {
            throw new IllegalArgumentException("仅支持 JPG、PNG、GIF、WEBP 格式");
        }
        // 文件大小校验
        if (file.getSize() > MAX_SIZE) {
            throw new IllegalArgumentException("图片大小不能超过 5MB");
        }

        // 生成唯一 ObjectKey：avatars/{userId}/{uuid}.{ext}
        String originalName = file.getOriginalFilename();
        String ext = (originalName != null && originalName.contains("."))
                ? originalName.substring(originalName.lastIndexOf("."))
                : ".jpg";
        String objectKey = "avatars/" + userId + "/" + UUID.randomUUID() + ext;

        // 上传
        OSS ossClient = new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);
        try {
            ObjectMetadata meta = new ObjectMetadata();
            meta.setContentType(contentType);
            meta.setContentLength(file.getSize());
            ossClient.putObject(bucketName, objectKey, file.getInputStream(), meta);
        } finally {
            ossClient.shutdown();
        }

        // 拼接公网 URL（去掉 http:// 前缀，改为 https）
        String host = endpoint.replaceFirst("https?://", "");
        String url = "https://" + bucketName + "." + host + "/" + objectKey;
        log.info("OSS 上传成功: userId={} url={}", userId, url);
        return url;
    }
}
