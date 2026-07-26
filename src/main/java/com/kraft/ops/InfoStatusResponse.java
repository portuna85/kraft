package com.kraft.ops;

public record InfoStatusResponse(String service, String status, String timezone, String checkedAt) {
}
