package com.example.compress_video_app.models;

import android.app.Dialog;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.compress_video_app.R;
import com.example.compress_video_app.adapters.CompressedVideosAdapter;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.ArrayList;

public class CompressedVideosDialog extends BottomSheetDialogFragment {

    ArrayList<HandleVideo> arrayList = new ArrayList<>();
    CompressedVideosAdapter videoFilesAdapter;
    BottomSheetDialog bottomSheetDialog;
    RecyclerView recyclerView;
    TextView folder;

    private final ElementListener fileListener;

    public CompressedVideosDialog(ArrayList<HandleVideo> arrayList, CompressedVideosAdapter videoFilesAdapter, ElementListener elementListener) {
        this.arrayList = arrayList;
        this.videoFilesAdapter = videoFilesAdapter;
        this.fileListener = elementListener;
    }

    public void updateList(ArrayList<HandleVideo> list){
        if(arrayList!= null && videoFilesAdapter != null){
            arrayList = list;
            videoFilesAdapter.notifyDataSetChanged();
        }
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {

        bottomSheetDialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);
        View view = LayoutInflater.from(getContext()).inflate(R.layout.playlist_bs_layout, null);
        bottomSheetDialog.setContentView(view);

        recyclerView = view.findViewById(R.id.playlist_rv);
        folder = view.findViewById(R.id.playlist_name);

        folder.setText("Compressed videos");

        videoFilesAdapter = new CompressedVideosAdapter(arrayList, getContext(), new CompressedVideosAdapter.CompressedVideoListener() {
            @Override
            public void onClickHandle(Uri uri) {
                fileListener.onClickHandle(uri);
                bottomSheetDialog.dismiss();
            }
        });
        LinearLayoutManager layoutManager = new LinearLayoutManager(getContext(), RecyclerView.VERTICAL, false);
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setAdapter(videoFilesAdapter);
        videoFilesAdapter.notifyDataSetChanged();

        return bottomSheetDialog;

    }

    public interface ElementListener {
        void onClickHandle(Uri uri);
    }


}
