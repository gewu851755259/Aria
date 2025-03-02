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

package com.arialyy.simple.core.download.m3u8;

import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProviders;
import com.arialyy.annotations.Download;
import com.arialyy.annotations.M3U8;
import com.arialyy.aria.core.Aria;
import com.arialyy.aria.core.common.controller.BuilderController;
import com.arialyy.aria.core.download.DownloadEntity;
import com.arialyy.aria.core.download.M3U8Entity;
import com.arialyy.aria.core.download.m3u8.M3U8VodOption;
import com.arialyy.aria.core.inf.IEntity;
import com.arialyy.aria.core.processor.IBandWidthUrlConverter;
import com.arialyy.aria.core.processor.ITsMergeHandler;
import com.arialyy.aria.core.processor.IVodTsUrlConverter;
import com.arialyy.aria.core.task.DownloadTask;
import com.arialyy.aria.util.ALog;
import com.arialyy.aria.util.CommonUtil;
import com.arialyy.frame.util.show.T;
import com.arialyy.simple.R;
import com.arialyy.simple.base.BaseActivity;
import com.arialyy.simple.common.ModifyPathDialog;
import com.arialyy.simple.common.ModifyUrlDialog;
import com.arialyy.simple.databinding.ActivityM3u8VodBinding;
import com.arialyy.simple.to.PeerIndex;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

public class M3U8VodDLoadActivity extends BaseActivity<ActivityM3u8VodBinding> {

  private String mUrl;
  private String mFilePath;
  private M3U8VodModule mModule;
  private VideoPlayerFragment mVideoFragment;
  private long mTaskId;

  @Override
  protected void init(Bundle savedInstanceState) {
    super.init(savedInstanceState);
    setTitle(getString(R.string.m3u8_file));
    Aria.download(this).register();
    mModule = ViewModelProviders.of(this).get(M3U8VodModule.class);
    mModule.getHttpDownloadInfo(this).observe(this, new Observer<DownloadEntity>() {

      @Override public void onChanged(@Nullable DownloadEntity entity) {
        if (entity == null) {
          return;
        }
        mTaskId = entity.getId();
        if (entity.getState() == IEntity.STATE_STOP) {
          getBinding().setStateStr(getString(R.string.resume));
        } else if (entity.getState() == IEntity.STATE_RUNNING) {
          getBinding().setStateStr(getString(R.string.stop));
        }

        if (entity.getFileSize() != 0) {
          getBinding().setFileSize(CommonUtil.formatFileSize(entity.getFileSize()));
        }
        getBinding().setProgress(entity.getPercent());
        getBinding().setUrl(entity.getUrl());
        getBinding().setFilePath(entity.getFilePath());
        mUrl = entity.getUrl();
        mFilePath = entity.getFilePath();
        mVideoFragment = new VideoPlayerFragment(0, entity);
        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
        ft.add(R.id.video_content, mVideoFragment);
        ft.commit();
      }
    });
    getBinding().setViewModel(this);
  }

  public void chooseUrl() {
    ModifyUrlDialog dialog =
        new ModifyUrlDialog(this, getString(R.string.modify_url_dialog_title), mUrl);
    dialog.show(getSupportFragmentManager(), "ModifyUrlDialog");
  }

  public void chooseFilePath() {
    ModifyPathDialog dialog =
        new ModifyPathDialog(this, getString(R.string.modify_file_path), mFilePath);
    dialog.show(getSupportFragmentManager(), "ModifyPathDialog");
  }

  @Override protected void onStart() {
    super.onStart();
    EventBus.getDefault().register(this);
  }

  @Override protected void onStop() {
    super.onStop();
    EventBus.getDefault().unregister(this);
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  public void jumpIndex(PeerIndex index) {
    Aria.download(this).load(mTaskId).m3u8VodOption().jumPeerIndex(index.index);
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    getMenuInflater().inflate(R.menu.menu_single_task_activity, menu);
    return super.onCreateOptionsMenu(menu);
  }

  @Override
  public boolean onMenuItemClick(MenuItem item) {
    int speed = -1;
    String msg = "";
    switch (item.getItemId()) {
      case R.id.help:
        msg = "一些小知识点：\n"
            + "1、你可以在注解中增加链接，用于指定被注解的方法只能被特定的下载任务回调，以防止progress乱跳\n"
            + "2、当遇到网络慢的情况时，你可以先使用onPre()更新UI界面，待连接成功时，再在onTaskPre()获取完整的task数据，然后给UI界面设置正确的数据\n"
            + "3、你可以在界面初始化时通过Aria.download(this).load(URL).getPercent()等方法快速获取相关任务的一些数据";
        showMsgDialog("tip", msg);
        break;
      case R.id.speed_0:
        speed = 0;
        break;
      case R.id.speed_128:
        speed = 128;
        break;
      case R.id.speed_256:
        speed = 256;
        break;
      case R.id.speed_512:
        speed = 512;
        break;
      case R.id.speed_1m:
        speed = 1024;
        break;
    }
    if (speed > -1) {
      msg = item.getTitle().toString();
      Aria.download(this).setMaxSpeed(speed);
      T.showShort(this, msg);
    }
    return true;
  }

  @M3U8.onPeerStart
  void onPeerStart(String m3u8Url, String peerPath, int peerIndex) {
    //ALog.d(TAG, "peer create, path: " + peerPath + ", index: " + peerIndex);
  }

  @M3U8.onPeerComplete
  void onPeerComplete(String m3u8Url, String peerPath, int peerIndex) {
    //ALog.d(TAG, "peer complete, path: " + peerPath + ", index: " + peerIndex);
    mVideoFragment.addPlayer(peerIndex, peerPath);
  }

  @M3U8.onPeerFail
  void onPeerFail(String m3u8Url, String peerPath, int peerIndex) {
    //ALog.d(TAG, "peer fail, path: " + peerPath + ", index: " + peerIndex);
  }

  @Download.onWait
  void onWait(DownloadTask task) {
    if (task.getKey().equals(mUrl)) {
      Log.d(TAG, "wait ==> " + task.getDownloadEntity().getFileName());
    }
  }

  @Download.onPre
  protected void onPre(DownloadTask task) {
    if (task.getKey().equals(mUrl)) {
      getBinding().setStateStr(getString(R.string.stop));
    }
  }

  @Download.onTaskStart
  void taskStart(DownloadTask task) {
    if (task.getKey().equals(mUrl)) {
      getBinding().setFileSize(task.getConvertFileSize());
      ALog.d(TAG, "isComplete = " + task.isComplete() + ", state = " + task.getState());
    }
  }

  @Download.onTaskRunning
  protected void running(DownloadTask task) {
    if (task.getKey().equals(mUrl)) {
      //ALog.d(TAG, "isRunning");
      getBinding().setProgress(task.getPercent());
      getBinding().setSpeed(task.getConvertSpeed());
    }
  }

  @Download.onTaskResume
  void taskResume(DownloadTask task) {
    if (task.getKey().equals(mUrl)) {
      getBinding().setStateStr(getString(R.string.stop));
    }
  }

  @Download.onTaskStop
  void taskStop(DownloadTask task) {
    if (task.getKey().equals(mUrl)) {
      getBinding().setStateStr(getString(R.string.resume));
      getBinding().setSpeed("");
    }
  }

  @Download.onTaskCancel
  void taskCancel(DownloadTask task) {
    if (task.getKey().equals(mUrl)) {
      getBinding().setProgress(0);
      getBinding().setStateStr(getString(R.string.start));
      getBinding().setSpeed("");
      Log.d(TAG, "cancel");
    }
  }

  @Download.onTaskFail
  void taskFail(DownloadTask task, Exception e) {
    if (task.getKey().equals(mUrl)) {
      Toast.makeText(M3U8VodDLoadActivity.this, getString(R.string.download_fail),
          Toast.LENGTH_SHORT)
          .show();
      getBinding().setStateStr(getString(R.string.start));
      getBinding().setSpeed("");
      Log.d(TAG, "fail");
    }
  }

  @Download.onTaskComplete
  void taskComplete(DownloadTask task) {
    if (task.getKey().equals(mUrl)) {
      getBinding().setProgress(100);
      Toast.makeText(M3U8VodDLoadActivity.this, getString(R.string.download_success),
          Toast.LENGTH_SHORT).show();
      getBinding().setStateStr(getString(R.string.re_start));
      getBinding().setSpeed("");
      ALog.d(TAG, "md5: " + CommonUtil.getFileMD5(new File(task.getFilePath())));
    }
  }

  @Override
  protected int setLayoutId() {
    return R.layout.activity_m3u8_vod;
  }

  public void onClick(View view) {
    switch (view.getId()) {
      case R.id.start:
        if (mTaskId == -1) {
          startD();
          break;
        }
        if (Aria.download(this).load(mTaskId).isRunning()) {
          Aria.download(this).load(mTaskId).stop();
        } else {
          Aria.download(this)
              .load(mTaskId)
              .m3u8VodOption(getM3U8Option())
              .resume();
        }
        break;
      case R.id.cancel:
        Aria.download(this).load(mTaskId).cancel(true);
        mTaskId = -1;
        break;
    }
  }

  private void startD() {
    mTaskId = Aria.download(M3U8VodDLoadActivity.this)
        .load(mUrl)
        .setFilePath(mFilePath, true)
        .m3u8VodOption(getM3U8Option())
        .create();
  }

  private M3U8VodOption getM3U8Option() {
    M3U8VodOption option = new M3U8VodOption();
    option
        .generateIndexFile()
        .setVodTsUrlConvert(new VodTsUrlConverter())
        .setMergeHandler(new TsMergeHandler());
    //.setBandWidthUrlConverter(new BandWidthUrlConverter(mUrl));
    return option;
  }

  @Override protected void dataCallback(int result, Object data) {
    super.dataCallback(result, data);
    if (result == ModifyUrlDialog.MODIFY_URL_DIALOG_RESULT) {
      mModule.uploadUrl(this, String.valueOf(data));
    } else if (result == ModifyPathDialog.MODIFY_PATH_RESULT) {
      mModule.updateFilePath(this, String.valueOf(data));
    }
  }

  static class VodTsUrlConverter implements IVodTsUrlConverter {
    @Override public List<String> convert(String m3u8Url, List<String> tsUrls) {
      Uri uri = Uri.parse(m3u8Url);
      String parentUrl = "http://" + uri.getHost();
      List<String> newUrls = new ArrayList<>();
      for (String url : tsUrls) {
        newUrls.add(parentUrl + url);
      }

      return newUrls;
    }
  }

  static class TsMergeHandler implements ITsMergeHandler {
    public boolean merge(@Nullable M3U8Entity m3U8Entity, List<String> tsPath) {
      ALog.d("TsMergeHandler", "合并TS....");
      return false;
    }
  }

  static class BandWidthUrlConverter implements IBandWidthUrlConverter {

    private String url;

    BandWidthUrlConverter(String url) {
      this.url = url;
    }

    @Override public String convert(String bandWidthUrl) {
      int index = url.lastIndexOf("/");
      return url.substring(0, index + 1) + bandWidthUrl;
    }
  }
}