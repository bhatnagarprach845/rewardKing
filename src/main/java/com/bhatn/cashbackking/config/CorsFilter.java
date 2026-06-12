package com.bhatn.cashbackking.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorsFilter implements Filter {

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) res;

        // 1. Fetch the dynamic incoming origin string from the browser request
        String incomingOrigin = request.getHeader("Origin");

        if (incomingOrigin != null) {
            // 2. Core Fix: Validate if the origin is localhost or matching your Amplify domain profile
            if (incomingOrigin.equals("http://localhost:3000") ||
                    incomingOrigin.endsWith(".amplifyapp.com")) {

                // Echo the verified valid origin right back to the client headers list
                response.setHeader("Access-Control-Allow-Origin", incomingOrigin);
            }
        }


        response.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Authorization, Content-Type, Accept");
        response.setHeader("Access-Control-Allow-Credentials", "true");
        response.setHeader("Access-Control-Max-Age", "3600");

        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            response.setStatus(HttpServletResponse.SC_OK);
            return;
        }

        chain.doFilter(req, res);
    }
}