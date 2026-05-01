package com.xiaozhi.controller;

import com.xiaozhi.common.web.AjaxResult;
import com.xiaozhi.common.web.HttpStatus;
import com.xiaozhi.utils.CmsUtils;
import com.xiaozhi.utils.FirmwareUtils;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.HexFormat;

/**
 * 文件下载控制器
 * 用于处理固件文件的上传和下载
 *
 * @author Joey
 */
@RestController
@RequestMapping("/api/file")
public class FileDownloadController extends BaseController {

    private static final Logger logger = LoggerFactory.getLogger(FileDownloadController.class);

    @Value("${xiaozhi.file.download-url-prefix:}")
    private String downloadUrlPrefix;

    @Resource
    private CmsUtils cmsUtils;

    /**
     * 上传固件文件（需要登录）
     *
     * @param file 上传的文件
     * @param version 固件版本号
     * @param description 文件描述
     * @return 上传结果
     */
    @PostMapping("/firmware/upload")
    @ResponseBody
    public AjaxResult uploadFirmware(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "version", required = false) String version,
            @RequestParam(value = "description", required = false) String description,
            HttpServletRequest request) {

        try {
            // 检查用户是否已登录
            if (!CmsUtils.isUserLoggedIn(request)) {
                return AjaxResult.error("请先登录");
            }

            // 验证文件
            if (file.isEmpty()) {
                return AjaxResult.error("文件不能为空");
            }

            String originalFilename = file.getOriginalFilename();
            if (originalFilename == null || !originalFilename.toLowerCase().endsWith(".bin")) {
                return AjaxResult.error("只支持.bin格式的固件文件");
            }

            String firmwareVersion;
            try {
                Optional<String> resolvedVersion = FirmwareUtils.resolveVersion(version, originalFilename);
                if (resolvedVersion.isEmpty()) {
                    return AjaxResult.error(HttpStatus.BAD_REQUEST, "请填写版本号，或将文件命名为 firmware_v2.3.0.bin 这样的格式");
                }
                firmwareVersion = resolvedVersion.get();
            } catch (IllegalArgumentException e) {
                return AjaxResult.error(HttpStatus.BAD_REQUEST, e.getMessage());
            }

            String effectiveUploadPath = cmsUtils.getEffectiveUploadPath();
            File firmwareRoot = new File(effectiveUploadPath + File.separator + "firmware");
            String sha256 = calculateSha256(file);

            File duplicateFile = findDuplicateFirmwareFile(firmwareRoot, sha256);
            if (duplicateFile != null) {
                return AjaxResult.error(
                        HttpStatus.CONFLICT,
                        "该固件内容已存在，已阻止重复上传",
                        buildFirmwareInfo(duplicateFile));
            }

            File sameVersionFile = findFirmwareFileByVersion(firmwareRoot, firmwareVersion);
            if (sameVersionFile != null) {
                return AjaxResult.error(
                        HttpStatus.CONFLICT,
                        "版本 " + firmwareVersion + " 已存在，请先删除旧文件或上传新版本号",
                        buildFirmwareInfo(sameVersionFile));
            }

            String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
            String firmwareDir = effectiveUploadPath + File.separator + "firmware" + File.separator + datePath;
            File dir = new File(firmwareDir);
            if (!dir.exists()) {
                boolean created = dir.mkdirs();
                if (!created) {
                    logger.error("无法创建目录: {}", firmwareDir);
                    return AjaxResult.error("无法创建上传目录");
                }
                logger.info("创建上传目录成功: {}", firmwareDir);
            }

            String fileName = FirmwareUtils.buildStorageFileName(firmwareVersion, sha256);
            Path filePath = Paths.get(firmwareDir, fileName);
            logger.info("准备保存固件文件: {}", filePath);

            try {
                Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);
                logger.info("固件文件保存成功: {}", filePath);
            } catch (Exception e) {
                logger.error("固件文件保存失败: {}", filePath, e);
                return AjaxResult.error("文件保存失败: " + e.getMessage());
            }

            String downloadUrl = generateDownloadUrl(fileName, datePath);

            logger.info("固件文件上传成功: {}, 用户: {}", fileName, CmsUtils.getUserId());

            AjaxResult result = AjaxResult.success("固件上传成功");
            result.put("fileName", fileName);
            result.put("originalName", originalFilename);
            result.put("downloadUrl", downloadUrl);
            result.put("version", firmwareVersion);
            result.put("description", description);
            result.put("sha256", sha256);
            result.put("hash", FirmwareUtils.extractHash(fileName).orElse(""));
            result.put("size", file.getSize());
            result.put("uploadTime", new Date());

            return result;

        } catch (Exception e) {
            logger.error("固件文件上传异常", e);
            return AjaxResult.error("上传异常: " + e.getMessage());
        }
    }

    /**
     * 下载固件文件（无需登录）
     *
     * @param fileName 文件名
     * @param response HTTP响应
     * @return 文件流
     */
    @GetMapping("/firmware/download/{fileName}")
    public ResponseEntity<org.springframework.core.io.Resource> downloadFirmware(
            @PathVariable String fileName,
            HttpServletResponse response) {

        try {
            // 验证文件名安全性
            if (!isValidFileName(fileName)) {
                return ResponseEntity.badRequest().build();
            }

            // 查找文件
            File file = findFirmwareFile(fileName);
            if (file == null || !file.exists()) {
                logger.warn("固件文件不存在: {}", fileName);
                return ResponseEntity.notFound().build();
            }

            // 设置响应头
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentDispositionFormData("attachment", fileName);
            headers.setContentLength(file.length());

            // 设置缓存控制
            headers.setCacheControl("public, max-age=3600"); // 1小时缓存

            org.springframework.core.io.Resource resource = new FileSystemResource(file);

            logger.info("固件文件下载: {}, 大小: {} bytes", fileName, file.length());

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(resource);

        } catch (Exception e) {
            logger.error("固件文件下载失败: {}", fileName, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 获取固件文件列表（需要登录）
     *
     * @return 文件列表
     */
    @GetMapping("/firmware/list")
    @ResponseBody
    public AjaxResult listFirmwareFiles(HttpServletRequest request) {
        try {
            // 检查用户是否已登录
            if (!CmsUtils.isUserLoggedIn(request)) {
                return AjaxResult.error("请先登录");
            }

            List<Map<String, Object>> fileList = new ArrayList<>();
            String effectiveUploadPath = cmsUtils.getEffectiveUploadPath();
            File firmwareDir = new File(effectiveUploadPath + File.separator + "firmware");

            if (firmwareDir.exists()) {
                scanFirmwareFiles(firmwareDir, fileList);
            }

            // 按修改时间倒序排列
            fileList.sort((a, b) -> {
                Date dateA = (Date) a.get("modifyTime");
                Date dateB = (Date) b.get("modifyTime");
                return dateB.compareTo(dateA);
            });

            AjaxResult result = AjaxResult.success();
            result.put("files", fileList);
            result.put("total", fileList.size());
            result.put("latest", fileList.isEmpty() ? null : fileList.get(0));

            return result;

        } catch (Exception e) {
            logger.error("获取固件文件列表失败", e);
            return AjaxResult.error("获取文件列表失败: " + e.getMessage());
        }
    }

    /**
     * 删除固件文件（需要登录）
     *
     * @param fileName 文件名
     * @return 删除结果
     */
    @DeleteMapping("/firmware/{fileName}")
    @ResponseBody
    public AjaxResult deleteFirmwareFile(
            @PathVariable String fileName,
            HttpServletRequest request) {

        try {
            // 检查用户是否已登录
            if (!CmsUtils.isUserLoggedIn(request)) {
                return AjaxResult.error("请先登录");
            }

            // 验证文件名安全性
            if (!isValidFileName(fileName)) {
                return AjaxResult.error("无效的文件名");
            }

            // 查找并删除文件
            File file = findFirmwareFile(fileName);
            if (file == null || !file.exists()) {
                return AjaxResult.error("文件不存在");
            }

            if (file.delete()) {
                logger.info("固件文件删除成功: {}, 用户: {}", fileName, CmsUtils.getUserId());
                return AjaxResult.success("文件删除成功");
            } else {
                return AjaxResult.error("文件删除失败");
            }

        } catch (Exception e) {
            logger.error("删除固件文件失败: {}", fileName, e);
            return AjaxResult.error("删除文件失败: " + e.getMessage());
        }
    }

    /**
     * 生成下载URL
     */
    private String generateDownloadUrl(String fileName, String datePath) {
        if (StringUtils.hasText(downloadUrlPrefix)) {
            return downloadUrlPrefix + "/api/file/firmware/download/" + fileName;
        } else {
            // 使用当前服务器地址
            String serverAddress = cmsUtils.getServerAddress();
            return serverAddress + "/api/file/firmware/download/" + fileName;
        }
    }

    /**
     * 验证文件名安全性
     */
    private boolean isValidFileName(String fileName) {
        if (!StringUtils.hasText(fileName)) {
            return false;
        }
        // 只允许字母、数字、点、下划线、连字符
        return fileName.matches("^[a-zA-Z0-9._-]+\\.bin$");
    }

    /**
     * 查找固件文件
     */
    private File findFirmwareFile(String fileName) {
        String effectiveUploadPath = cmsUtils.getEffectiveUploadPath();
        File firmwareDir = new File(effectiveUploadPath + File.separator + "firmware");
        if (!firmwareDir.exists()) {
            return null;
        }

        // 递归查找文件
        return findFileRecursively(firmwareDir, fileName);
    }

    /**
     * 递归查找文件
     */
    private File findFileRecursively(File dir, String fileName) {
        File[] files = dir.listFiles();
        if (files == null) {
            return null;
        }

        for (File file : files) {
            if (file.isDirectory()) {
                File found = findFileRecursively(file, fileName);
                if (found != null) {
                    return found;
                }
            } else if (file.getName().equals(fileName)) {
                return file;
            }
        }
        return null;
    }

    private File findDuplicateFirmwareFile(File dir, String sha256) {
        if (!dir.exists()) {
            return null;
        }

        File[] files = dir.listFiles();
        if (files == null) {
            return null;
        }

        for (File file : files) {
            if (file.isDirectory()) {
                File found = findDuplicateFirmwareFile(file, sha256);
                if (found != null) {
                    return found;
                }
            } else if (file.getName().toLowerCase().endsWith(".bin")) {
                try {
                    if (sha256.equalsIgnoreCase(calculateSha256(file))) {
                        return file;
                    }
                } catch (IOException e) {
                    logger.warn("计算已有固件哈希失败，已跳过: {}", file.getAbsolutePath(), e);
                }
            }
        }

        return null;
    }

    private File findFirmwareFileByVersion(File dir, String version) {
        if (!dir.exists()) {
            return null;
        }

        File[] files = dir.listFiles();
        if (files == null) {
            return null;
        }

        for (File file : files) {
            if (file.isDirectory()) {
                File found = findFirmwareFileByVersion(file, version);
                if (found != null) {
                    return found;
                }
            } else if (file.getName().toLowerCase().endsWith(".bin")) {
                Optional<String> fileVersion = FirmwareUtils.extractVersion(file.getName());
                if (fileVersion.isPresent() && fileVersion.get().equalsIgnoreCase(version)) {
                    return file;
                }
            }
        }

        return null;
    }

    private Map<String, Object> buildFirmwareInfo(File file) {
        Map<String, Object> fileInfo = new HashMap<>();
        fileInfo.put("fileName", file.getName());
        fileInfo.put("version", FirmwareUtils.extractVersion(file.getName()).orElse(""));
        fileInfo.put("hash", FirmwareUtils.extractHash(file.getName()).orElse(""));
        fileInfo.put("size", file.length());
        fileInfo.put("modifyTime", new Date(file.lastModified()));
        fileInfo.put("downloadUrl", generateDownloadUrl(file.getName(), ""));
        return fileInfo;
    }

    private String calculateSha256(MultipartFile file) throws IOException {
        try (InputStream inputStream = file.getInputStream()) {
            return calculateSha256(inputStream);
        }
    }

    private String calculateSha256(File file) throws IOException {
        try (InputStream inputStream = Files.newInputStream(file.toPath())) {
            return calculateSha256(inputStream);
        }
    }

    private String calculateSha256(InputStream inputStream) throws IOException {
        MessageDigest digest = newSha256Digest();
        try (DigestInputStream digestInputStream = new DigestInputStream(inputStream, digest)) {
            byte[] buffer = new byte[8192];
            while (digestInputStream.read(buffer) != -1) {
                // DigestInputStream updates the digest while bytes are consumed.
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private MessageDigest newSha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("当前运行环境不支持 SHA-256", e);
        }
    }

    /**
     * 扫描固件文件
     */
    private void scanFirmwareFiles(File dir, List<Map<String, Object>> fileList) {
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }

        for (File file : files) {
            if (file.isDirectory()) {
                scanFirmwareFiles(file, fileList);
            } else if (file.getName().toLowerCase().endsWith(".bin")) {
                fileList.add(buildFirmwareInfo(file));
            }
        }
    }
}
