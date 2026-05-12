package com.volunteer.common.utils;

import org.springframework.beans.BeanUtils;
import java.util.List;
import java.util.stream.Collectors;

public class BeanCopyUtils {

    private BeanCopyUtils() {}

    public static <V> V copyBean(Object source, Class<V> clazz) {
        try {
            V result = clazz.newInstance();
            BeanUtils.copyProperties(source, result);
            return result;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static <O, V> List<V> copyBeanList(List<O> list, Class<V> clazz) {
        return list.stream().map(o -> copyBean(o, clazz)).collect(Collectors.toList());
    }
}
