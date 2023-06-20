package com.example.compress_video_app.database;

import com.example.compress_video_app.models.HandleVideo;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import io.reactivex.Completable;

@Dao
public interface CompressedVideoDao {

    @Query("SELECT * FROM videos WHERE name = :videoName LIMIT 1")
    HandleVideo getVideoByName(String videoName);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertVideo(HandleVideo video);

    @Update
    void updateVideo(HandleVideo video);

    @Delete
    void deleteVideo(HandleVideo video);

    @Query("delete from videos where name = :tName")
    void deleteVideoByName(String tName);

    @Query("delete from videos where name like '%' || :parentName || '%'")
    void deleteVideoByParentName(String parentName);
}
