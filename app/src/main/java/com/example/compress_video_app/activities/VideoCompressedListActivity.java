package com.example.compress_video_app.activities;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.SearchView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.compress_video_app.R;
import com.example.compress_video_app.adapters.CompressedVideosAdapter;
import com.example.compress_video_app.database.CompressedVideoRepository;
import com.example.compress_video_app.models.HandleVideo;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class VideoCompressedListActivity extends AppCompatActivity implements SearchView.OnQueryTextListener {

    public static final String MY_PREF = "my pref";
    static CompressedVideosAdapter videoFilesAdapter;
    private RecyclerView recyclerView;
    private SwipeRefreshLayout swipeRefreshLayout;
    private List<File> videoFiles;
    private ArrayList<HandleVideo> videoHandleFiles;
    private CompressedVideoRepository repository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_files);
        getSupportActionBar().setTitle("Compressed videos");

        repository = new CompressedVideoRepository(getApplication());

        recyclerView = findViewById(R.id.videos_rv);
        swipeRefreshLayout = findViewById(R.id.swipe_refresh_videos);

        showCompressVideos();
        swipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                showCompressVideos();
                swipeRefreshLayout.setRefreshing(false);
            }
        });
    }

    private void showCompressVideos() {
        videoFiles = fetchVideoFiles();
        videoHandleFiles = fetchHandleVideos();
        videoFilesAdapter = new CompressedVideosAdapter(videoHandleFiles, this, 2, new CompressedVideosAdapter.CompressedVideoListener() {
            @Override
            public void onClickHandle(Uri uri, int position) {
                HandleVideo video = repository.getVideoByName(new HandleVideo(uri).getName());

                HandleVideo parentVideo = new HandleVideo(Uri.fromFile(new File(video.getParentPath())));

                Intent intent = new Intent(VideoCompressedListActivity.this, CompressSingleActivity.class);
                intent.putExtra("position", position);
                intent.setData(Uri.fromFile(new File(video.getParentPath())));
                startActivity(intent);
            }
        });
        recyclerView.setAdapter(videoFilesAdapter);
        recyclerView.setLayoutManager(new LinearLayoutManager(this,
                RecyclerView.VERTICAL, false));
        videoFilesAdapter.notifyDataSetChanged();
    }

    private ArrayList<HandleVideo> fetchHandleVideos() {
        ArrayList<HandleVideo> videoFilesTemp = new ArrayList<>();

        for (File file : videoFiles) {
            HandleVideo video_temp;
            try {
                video_temp = new HandleVideo(Uri.fromFile(file));
                if (video_temp.getDuration() == 0)
                    file.delete();
                else
                    videoFilesTemp.add(video_temp);
            } catch (Exception e) {
                file.delete();
            }
        }
        return videoFilesTemp;
    }

    private List<File> fetchVideoFiles() {

        String folderPath = getFilesDir() + File.separator + "temp_videos";

        File folder = new File(folderPath);
        File[] files = folder.listFiles();

        List<File> videoFiles = new ArrayList<>();
        if (files != null) {
            for (File file : files) {
                if (isVideoFile(file)) {
                    videoFiles.add(file);
                }
            }
        }
        return videoFiles;
    }

    private boolean isVideoFile(File file) {
        String name = file.getName();
        String extension = name.substring(name.lastIndexOf(".") + 1).toLowerCase();
        return extension.equals("mp4") || extension.equals("mkv") || extension.equals("avi");
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.compress_menu, menu);
        MenuItem menuItem = menu.findItem(R.id.search_video_compress);
        SearchView searchView = (SearchView) menuItem.getActionView();
        searchView.setOnQueryTextListener(this);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        int id = item.getItemId();
        if (id == R.id.refresh_files_compress) {
            finish();
            startActivity(getIntent());
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onQueryTextSubmit(String query) {
        return false;
    }

    @Override
    public boolean onQueryTextChange(String newText) {
        String inputs = newText.toLowerCase();
        ArrayList<HandleVideo> mediaFiles = new ArrayList<>();
        for (HandleVideo media : videoHandleFiles) {
            if (media.getName().toLowerCase().contains(inputs)) {
                mediaFiles.add(media);
            }
        }
        VideoCompressedListActivity.videoFilesAdapter.updateVideoFiles(mediaFiles);
        return true;
    }

}