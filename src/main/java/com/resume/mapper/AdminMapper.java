package com.resume.mapper;

import com.resume.entity.Admin;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AdminMapper {
    Admin findByUsername(@Param("username") String username);
    int insert(Admin admin);
}
