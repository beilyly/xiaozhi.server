package com.xiaozhi.controller;

import com.xiaozhi.common.web.AjaxResult;
import com.xiaozhi.utils.CmsUtils;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

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

    @Value("${xiaozhi.file.upload-path:}")
    private String uploadPath;

    @Value("${xiaozhi.file.download-url-prefix:}")
    private String downloadUrlPrefix;

    @Resource
    private CmsUtils cmsUtils;

    /**
     * 获取有效的上传路径
     * 如果配置的路径不可用，则使用当前工程目录下的files文件夹
     */
    private String getEffectiveUploadPath() {
        // 如果配置了路径且目录存在或可创建，则使用配置的路径
        if (StringUtils.hasText(uploadPath)) {
            File configDir = new File(uploadPath);
            if (configDir.exists() || configDir.mkdirs()) {
                logger.info("使用配置的上传路径: {}", uploadPath);
                return uploadPath;
            } else {
                logger.warn("配置的上传路径不可用: {}", uploadPath);
            }
        }
        
        // 使用当前工程目录下的files文件夹
        String currentDir = System.getProperty("user.dir");
        String fallbackPath = currentDir + File.separator + "files";
        logger.info("使用默认上传路径: {}", fallbackPath);
        
        // 确保默认目录存在
        File fallbackDir = new File(fallbackPath);
        if (!fallbackDir.exists()) {
            boolean created = fallbackDir.mkdirs();
            if (created) {
                logger.info("创建默认上传目录成功: {}", fallbackPath);
            } else {
                logger.warn("无法创建默认上传目录: {}", fallbackPath);
            }
        }
        
        return fallbackPath;
    }

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

            // 获取有效的上传路径
            String effectiveUploadPath = getEffectiveUploadPath();
            
            // 创建上传目录
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

            // 生成文件名
            String timestamp = String.valueOf(System.currentTimeMillis());
            String fileName = "firmware_" + timestamp + ".bin";
            if (StringUtils.hasText(version)) {
                fileName = "firmware_v" + version + "_" + timestamp + ".bin";
            }

            // 保存文件
            Path filePath = Paths.get(firmwareDir, fileName);
            logger.info("准备保存文件到: {}", filePath.toString());
            logger.info("目录是否存在: {}", Files.exists(filePath.getParent()));
            logger.info("目录是否可写: {}", Files.isWritable(filePath.getParent()));
            
            try {
                Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);
                logger.info("文件保存成功: {}", filePath.toString());
            } catch (Exception e) {
                logger.error("文件保存失败: {}", filePath.toString(), e);
                return AjaxResult.error("文件保存失败: " + e.getMessage());
            }

            // 生成下载URL
            String downloadUrl = generateDownloadUrl(fileName, datePath);

            logger.info("固件文件上传成功: {}, 用户: {}", fileName, CmsUtils.getUserId());

            AjaxResult result = AjaxResult.success("固件上传成功");
            result.put("fileName", fileName);
            result.put("originalName", originalFilename);
            result.put("downloadUrl", downloadUrl);
            result.put("version", version);
            result.put("description", description);
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
            String effectiveUploadPath = getEffectiveUploadPath();
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
        String effectiveUploadPath = getEffectiveUploadPath();
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
            } else if (file.getName().endsWith(".bin")) {
                Map<String, Object> fileInfo = new HashMap<>();
                fileInfo.put("fileName", file.getName());
                fileInfo.put("size", file.length());
                fileInfo.put("modifyTime", new Date(file.lastModified()));
                fileInfo.put("downloadUrl", generateDownloadUrl(file.getName(), ""));
                fileList.add(fileInfo);
            }
        }
    }
}
