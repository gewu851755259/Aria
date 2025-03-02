/*
 * Copyright (C) 2016 AriaLyy(https://github.com/AriaLyy/Aria)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.arialyy.simple.core.download.mutil;

import android.content.Context;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import com.arialyy.aria.core.Aria;
import com.arialyy.simple.R;
import com.arialyy.simple.base.adapter.AbsHolder;
import com.arialyy.simple.base.adapter.AbsRVAdapter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by AriaL on 2017/1/6.
 */
final class FileListAdapter extends AbsRVAdapter<FileListEntity, FileListAdapter.FileListHolder> {

  //SparseBooleanArray mBtStates = new SparseBooleanArray();
  Map<String, Boolean> mBtStates = new ConcurrentHashMap<>();
  private Map<String, Integer> mPositions = new ConcurrentHashMap<>();

  public FileListAdapter(Context context, List<FileListEntity> data) {
    super(context, data);
    for (int i = 0, len = data.size(); i < len; i++) {
      mBtStates.put(data.get(i).key, true);
      mPositions.put(data.get(i).key, i);
    }
  }

  @Override protected FileListHolder getViewHolder(View convertView, int viewType) {
    return new FileListHolder(convertView);
  }

  @Override protected int setLayoutId(int type) {
    return R.layout.item_file_list;
  }

  @Override
  protected void bindData(FileListHolder holder, int position, final FileListEntity item) {
    holder.name.setText("文件名：" + item.name);
    holder.url.setText("下载地址：" + item.key);
    holder.url.setVisibility(item.isGroup ? View.GONE : View.VISIBLE);
    holder.path.setText("保存路径：" + item.downloadPath);
    if (mBtStates.get(item.key)) {
      holder.bt.setEnabled(true);
      holder.bt.setOnClickListener(new View.OnClickListener() {
        @Override public void onClick(View v) {
          Toast.makeText(getContext(), "开始下载：" + item.name, Toast.LENGTH_SHORT).show();
          if (item.isGroup) {
            Aria.download(getContext())
                .loadGroup(Arrays.asList(item.urls))
                .setSubFileName(Arrays.asList(item.names))
                .setDirPath(item.downloadPath)
                .setGroupAlias(item.name)
                .unknownSize()
                .create();
          } else {
            Aria.download(getContext())
                .load(item.key)
                .setFilePath(item.downloadPath)
                .create();
          }
        }
      });
    } else {
      holder.bt.setEnabled(false);
    }
  }

  public void updateBtState(String downloadUrl, boolean able) {
    Set<String> keys = mBtStates.keySet();
    for (String key : keys) {
      if (key.equals(downloadUrl)) {
        mBtStates.put(downloadUrl, able);
        notifyItemChanged(indexItem(downloadUrl));
        return;
      }
    }
  }

  private synchronized int indexItem(String url) {
    Set<String> keys = mPositions.keySet();
    for (String key : keys) {
      if (key.equals(url)) {
        int index = mPositions.get(key);
        //Log.d(TAG, "peerIndex ==> " + peerIndex);
        return index;
      }
    }
    return -1;
  }

  class FileListHolder extends AbsHolder {
    TextView name;
    TextView url;
    TextView path;
    Button bt;

    FileListHolder(View itemView) {
      super(itemView);
      name = findViewById(R.id.name);
      url = findViewById(R.id.download_url);
      path = findViewById(R.id.download_path);
      bt = findViewById(R.id.bt);
    }
  }
}
