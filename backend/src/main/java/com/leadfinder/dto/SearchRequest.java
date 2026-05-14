package com.leadfinder.dto;

import jakarta.validation.constraints.NotBlank;

public class SearchRequest {

    @NotBlank(message = "Name is required")
    private String name;

    private String company;
    private String title;

    public SearchRequest() {
    }

    public SearchRequest(String name, String company, String title) {
        this.name = name;
        this.company = company;
        this.title = title;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCompany() {
        return company;
    }

    public void setCompany(String company) {
        this.company = company;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }
}
