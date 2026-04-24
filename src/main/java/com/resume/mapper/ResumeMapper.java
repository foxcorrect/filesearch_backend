package com.resume.mapper;

import com.resume.entity.Resume;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ResumeMapper {
    List<Resume> findAll();
    Resume findById(@Param("id") Long id);
    int update(Resume resume);
}
