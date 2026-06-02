package com.churchmanagement.dto.dashboard;

public class RegionSubmissionProgressDto {
    private String regionName;
    private long submittedChurches;
    private long totalChurches;

    public RegionSubmissionProgressDto() {
    }

    public RegionSubmissionProgressDto(String regionName, long submittedChurches, long totalChurches) {
        this.regionName = regionName;
        this.submittedChurches = submittedChurches;
        this.totalChurches = totalChurches;
    }

    public String getRegionName() {
        return regionName;
    }

    public void setRegionName(String regionName) {
        this.regionName = regionName;
    }

    public long getSubmittedChurches() {
        return submittedChurches;
    }

    public void setSubmittedChurches(long submittedChurches) {
        this.submittedChurches = submittedChurches;
    }

    public long getTotalChurches() {
        return totalChurches;
    }

    public void setTotalChurches(long totalChurches) {
        this.totalChurches = totalChurches;
    }

    public double getProgress() {
        return totalChurches <= 0 ? 0 : (double) submittedChurches / totalChurches;
    }
}
