package pl.lendly.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("lendly.auth.rate-limits")
public class RateLimitProperties {
    private int registration = 5;
    private long registrationWindowSeconds = 3600;
    private int loginIp = 10;
    private int loginEmail = 10;
    private int loginWideIp = 60;
    private long loginWindowSeconds = 900;
    private int refresh = 30;
    private long refreshWindowSeconds = 60;
    private int google = 10;
    private long googleWindowSeconds = 900;
    private int callback = 20;
    private long callbackWindowSeconds = 900;
    private int link = 5;
    private long linkWindowSeconds = 900;
    private int csrfPerClient = 20;
    private int csrfGlobal = 10_000;
    private long csrfWindowSeconds = 60;
    private int listingCreate = 20;
    private long listingCreateWindowSeconds = 86_400;
    private int imageUpload = 60;
    private long imageUploadWindowSeconds = 3600;
    private int search = 120;
    private long searchWindowSeconds = 60;
    private int reservationCreate = 20;
    private long reservationCreateWindowSeconds = 86_400;
    private int reportCreate = 10;
    private long reportCreateWindowSeconds = 86_400;
    private int aiScan = 20;
    private long aiScanWindowSeconds = 3600;
    private int mission = 30;
    private long missionWindowSeconds = 3600;
    private int emailVerify = 10;
    private long emailVerifyWindowSeconds = 900;
    private int emailResend = 5;
    private long emailResendWindowSeconds = 3600;
    private int mfaVerify = 10;
    private long mfaVerifyWindowSeconds = 900;
    private int mfaManage = 10;
    private long mfaManageWindowSeconds = 900;

    public int getRegistration() { return registration; }
    public void setRegistration(int value) { registration = value; }
    public long getRegistrationWindowSeconds() { return registrationWindowSeconds; }
    public void setRegistrationWindowSeconds(long value) { registrationWindowSeconds = value; }
    public int getLoginIp() { return loginIp; }
    public void setLoginIp(int value) { loginIp = value; }
    public int getLoginEmail() { return loginEmail; }
    public void setLoginEmail(int value) { loginEmail = value; }
    public int getLoginWideIp() { return loginWideIp; }
    public void setLoginWideIp(int value) { loginWideIp = value; }
    public long getLoginWindowSeconds() { return loginWindowSeconds; }
    public void setLoginWindowSeconds(long value) { loginWindowSeconds = value; }
    public int getRefresh() { return refresh; }
    public void setRefresh(int value) { refresh = value; }
    public long getRefreshWindowSeconds() { return refreshWindowSeconds; }
    public void setRefreshWindowSeconds(long value) { refreshWindowSeconds = value; }
    public int getGoogle() { return google; }
    public void setGoogle(int value) { google = value; }
    public long getGoogleWindowSeconds() { return googleWindowSeconds; }
    public void setGoogleWindowSeconds(long value) { googleWindowSeconds = value; }
    public int getCallback() { return callback; }
    public void setCallback(int value) { callback = value; }
    public long getCallbackWindowSeconds() { return callbackWindowSeconds; }
    public void setCallbackWindowSeconds(long value) { callbackWindowSeconds = value; }
    public int getLink() { return link; }
    public void setLink(int value) { link = value; }
    public long getLinkWindowSeconds() { return linkWindowSeconds; }
    public void setLinkWindowSeconds(long value) { linkWindowSeconds = value; }
    public int getCsrfPerClient() { return csrfPerClient; }
    public void setCsrfPerClient(int value) { csrfPerClient = value; }
    public int getCsrfGlobal() { return csrfGlobal; }
    public void setCsrfGlobal(int value) { csrfGlobal = value; }
    public long getCsrfWindowSeconds() { return csrfWindowSeconds; }
    public void setCsrfWindowSeconds(long value) { csrfWindowSeconds = value; }
    public int getListingCreate() { return listingCreate; }
    public void setListingCreate(int value) { listingCreate = value; }
    public long getListingCreateWindowSeconds() { return listingCreateWindowSeconds; }
    public void setListingCreateWindowSeconds(long value) { listingCreateWindowSeconds = value; }
    public int getImageUpload() { return imageUpload; }
    public void setImageUpload(int value) { imageUpload = value; }
    public long getImageUploadWindowSeconds() { return imageUploadWindowSeconds; }
    public void setImageUploadWindowSeconds(long value) { imageUploadWindowSeconds = value; }
    public int getSearch() { return search; }
    public void setSearch(int value) { search = value; }
    public long getSearchWindowSeconds() { return searchWindowSeconds; }
    public void setSearchWindowSeconds(long value) { searchWindowSeconds = value; }
    public int getReservationCreate() { return reservationCreate; }
    public void setReservationCreate(int value) { reservationCreate = value; }
    public long getReservationCreateWindowSeconds() { return reservationCreateWindowSeconds; }
    public void setReservationCreateWindowSeconds(long value) { reservationCreateWindowSeconds = value; }
    public int getReportCreate() { return reportCreate; }
    public void setReportCreate(int value) { reportCreate = value; }
    public long getReportCreateWindowSeconds() { return reportCreateWindowSeconds; }
    public void setReportCreateWindowSeconds(long value) { reportCreateWindowSeconds = value; }
    public int getAiScan() { return aiScan; }
    public void setAiScan(int value) { aiScan = value; }
    public long getAiScanWindowSeconds() { return aiScanWindowSeconds; }
    public void setAiScanWindowSeconds(long value) { aiScanWindowSeconds = value; }
    public int getMission() { return mission; }
    public void setMission(int value) { mission = value; }
    public long getMissionWindowSeconds() { return missionWindowSeconds; }
    public void setMissionWindowSeconds(long value) { missionWindowSeconds = value; }
    public int getEmailVerify() { return emailVerify; }
    public void setEmailVerify(int value) { emailVerify = value; }
    public long getEmailVerifyWindowSeconds() { return emailVerifyWindowSeconds; }
    public void setEmailVerifyWindowSeconds(long value) { emailVerifyWindowSeconds = value; }
    public int getEmailResend() { return emailResend; }
    public void setEmailResend(int value) { emailResend = value; }
    public long getEmailResendWindowSeconds() { return emailResendWindowSeconds; }
    public void setEmailResendWindowSeconds(long value) { emailResendWindowSeconds = value; }
    public int getMfaVerify() { return mfaVerify; }
    public void setMfaVerify(int value) { mfaVerify = value; }
    public long getMfaVerifyWindowSeconds() { return mfaVerifyWindowSeconds; }
    public void setMfaVerifyWindowSeconds(long value) { mfaVerifyWindowSeconds = value; }
    public int getMfaManage() { return mfaManage; }
    public void setMfaManage(int value) { mfaManage = value; }
    public long getMfaManageWindowSeconds() { return mfaManageWindowSeconds; }
    public void setMfaManageWindowSeconds(long value) { mfaManageWindowSeconds = value; }
}
