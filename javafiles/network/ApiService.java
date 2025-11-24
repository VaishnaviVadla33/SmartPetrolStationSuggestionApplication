package com.example.findingbunks_part2.network;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;

public interface ApiService {
    @GET("/hello")
    Call<HelloResponse> sayHello();

    @POST("/register")
    Call<RegisterResponse> register(@Body RegisterRequest req);
}
