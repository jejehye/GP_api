package com.example.gpapi.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AccountRequest {
    private String pw;
    private String account;

    @JsonProperty("bank_pw")
    private String bankPw;
}
