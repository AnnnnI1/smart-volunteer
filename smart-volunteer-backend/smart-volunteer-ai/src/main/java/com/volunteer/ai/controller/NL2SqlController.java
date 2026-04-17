package com.volunteer.ai.controller;

import com.volunteer.ai.dto.NL2SqlDTO;
import com.volunteer.ai.service.NL2SqlService;
import com.volunteer.common.entity.ResponseResult;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/ai/nl2sql")
public class NL2SqlController {

    @Autowired
    private NL2SqlService nl2SqlService;

    /**
     * 自然语言查询
     * 输入自然语言，由 DeepSeek 生成 SQL，执行后返回结果
     */
    @PostMapping("/query")
    public ResponseResult query(@RequestBody @Valid NL2SqlDTO dto) {
        return nl2SqlService.query(dto.getQuery());
    }
}
