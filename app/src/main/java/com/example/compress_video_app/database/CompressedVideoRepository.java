package com.example.compress_video_app.database;

import android.app.Application;

import com.example.compress_video_app.models.HandleVideo;

public class CompressedVideoRepository {
    private final CompressedVideoDao mDao;

    public CompressedVideoRepository(Application application) {
        CompressedVideoDatabase db = CompressedVideoDatabase.getDatabase(application);
        mDao = db.compressedVideoDao();
    }

    public HandleVideo getVideoByName(String videoName) {
        return mDao.getVideoByName(videoName);
    }

    public void insertVideo(HandleVideo video) {
        mDao.insertVideo(video);
    }

    public void updateVideo(HandleVideo video) {
        mDao.updateVideo(video);
    }

    public void deleteVideo(HandleVideo video) {
        mDao.deleteVideo(video);
    }

    public void deleteVideoByName(String tName) {
        mDao.deleteVideoByName(tName);
    }

    public void deleteVideoByParentName(String parentName) {
        mDao.deleteVideoByParentName(parentName);
    }
}
