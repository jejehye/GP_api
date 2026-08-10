package com.example.gpapi.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OpenScreenRequest {
    private String account;
    private String pw;
    private String screenNo;
    private String jcode;
}
