package com.github.ksewen.kelios.platform;
import java.util.List;
public record Page<T>(List<T> items,int page,int size,long totalElements) {}
