package io.github.team404.tikitaka.global.response;

public record BaseResponse<T>(
        String code,
        String message,
        T data
) {

    private static final String SUCCESS_CODE = "SUCCESS";

    public static <T> BaseResponse<T> success(String message, T data) {
        return new BaseResponse<>(SUCCESS_CODE, message, data);
    }

    public static BaseResponse<Void> success(String message) {
        return new BaseResponse<>(SUCCESS_CODE, message, null);
    }

    public static BaseResponse<Void> error(String code, String message) {
        return new BaseResponse<>(code, message, null);
    }
}
