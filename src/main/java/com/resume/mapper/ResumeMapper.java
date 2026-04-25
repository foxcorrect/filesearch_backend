package com.resume.mapper;

import com.resume.entity.Resume;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ResumeMapper {
    List<Resume> findAll(@Param("offset") int offset, @Param("limit") int limit);
    long countAll();
    Resume findById(@Param("id") Long id);
    Resume findFileDataById(@Param("id") Long id);
    int insert(Resume resume);
    int update(Resume resume);
    int updateFileData(@Param("id") Long id, @Param("fileData") byte[] fileData);
    int deleteById(@Param("id") Long id);
}
