package pl.lendly.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("lendly.storage")
public class StorageProperties {
    private String imageDir = "";

    public String getImageDir() { return imageDir; }
    public void setImageDir(String imageDir) { this.imageDir = imageDir == null ? "" : imageDir.trim(); }
}
