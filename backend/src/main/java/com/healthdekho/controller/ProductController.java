package com.healthdekho.controller;

import com.healthdekho.dto.ApiDtos.ProductDto;
import com.healthdekho.service.ProductService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/products")
public class ProductController {
    private final ProductService products;
    public ProductController(ProductService products) { this.products = products; }
    @GetMapping
    public List<ProductDto> active() { return products.activeProducts(); }
    @GetMapping("/available")
    public List<ProductDto> available(@AuthenticationPrincipal Jwt jwt) { return products.availableProducts(jwt.getSubject()); }
}
