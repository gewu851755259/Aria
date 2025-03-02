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
package com.arialyy.simple.core.upload;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import androidx.annotation.Nullable;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProviders;
import com.arialyy.annotations.Upload;
import com.arialyy.aria.core.Aria;
import com.arialyy.aria.core.common.FtpOption;
import com.arialyy.aria.core.inf.IEntity;
import com.arialyy.aria.core.task.UploadTask;
import com.arialyy.aria.core.upload.UploadEntity;
import com.arialyy.aria.util.ALog;
import com.arialyy.aria.util.CommonUtil;
import com.arialyy.frame.util.FileUtil;
import com.arialyy.frame.util.show.T;
import com.arialyy.simple.R;
import com.arialyy.simple.base.BaseActivity;
import com.arialyy.simple.common.ModifyUrlDialog;
import com.arialyy.simple.databinding.ActivityFtpUploadBinding;
import com.arialyy.simple.util.AppUtil;
import java.io.File;
import java.io.IOException;

/**
 * Created by lyy on 2017/7/28. Ftp 文件上传demo
 */
public class FtpUploadActivity extends BaseActivity<ActivityFtpUploadBinding> {
  private final int OPEN_FILE_MANAGER_CODE = 0xB1;
  private String mFilePath;
  private String mUrl;
  private UploadModule mModule;
  private long mTaskId = -1;
  private String user = "lao", pwd = "123456";

  @Override protected void init(Bundle savedInstanceState) {
    setTile("D_FTP 文件上传");
    super.init(savedInstanceState);
    Aria.upload(this).register();

    getBinding().setViewModel(this);
    setUI();
  }

  private void setUI() {
    mModule = ViewModelProviders.of(this).get(UploadModule.class);
    mModule.getFtpInfo(this).observe(this, new Observer<UploadEntity>() {
      @Override public void onChanged(@Nullable UploadEntity entity) {
        if (entity != null) {
          mTaskId = entity.getId();
          if (entity.getFileSize() != 0) {
            getBinding().setFileSize(CommonUtil.formatFileSize(entity.getFileSize()));
            getBinding().setProgress(entity.isComplete() ? 100
                : (int) (entity.getCurrentProgress() * 100 / entity.getFileSize()));
          }
          getBinding().setUrl(entity.getUrl());
          getBinding().setFilePath(entity.getFilePath());
          mUrl = entity.getUrl();
          mFilePath = entity.getFilePath();
          getBinding().setStateStr(getString(
              entity.getState() == IEntity.STATE_RUNNING ? R.string.stop : R.string.start));
        } else {
          getBinding().setStateStr(getString(R.string.resume));
        }
      }
    });
    setHelpCode();
  }

  private void setHelpCode() {
    try {
      getBinding().codeView.setSource(AppUtil.getHelpCode(this, "FtpUpload.java"));
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  @Override protected int setLayoutId() {
    return R.layout.activity_ftp_upload;
  }

  public void chooseUrl() {
    ModifyUrlDialog dialog =
        new ModifyUrlDialog(this, getString(R.string.modify_url_dialog_title), mUrl);
    dialog.show(getSupportFragmentManager(), "ModifyUrlDialog");
  }

  public void chooseFilePath() {
    AppUtil.chooseFile(this, new File(mFilePath), null, OPEN_FILE_MANAGER_CODE);
  }

  public void onClick(View view) {
    switch (view.getId()) {
      case R.id.start:
        if (mTaskId == -1) {
          mTaskId = Aria.upload(this)
              .loadFtp(mFilePath)
              .setUploadUrl(mUrl)
              .option(getOption())
              .create();
          getBinding().setStateStr(getString(R.string.stop));
          break;
        }
        if (Aria.upload(this).loadFtp(mTaskId).isRunning()) {
          Aria.upload(this).loadFtp(mTaskId).stop();
          getBinding().setStateStr(getString(R.string.resume));
        } else {
          Aria.upload(this)
              .loadFtp(mTaskId)
              .option(getOption())
              .resume();
          getBinding().setStateStr(getString(R.string.stop));
        }

        break;
      case R.id.cancel:
        Aria.upload(this).loadFtp(mTaskId).cancel();
        mTaskId = -1;
        getBinding().setStateStr(getString(R.string.start));
        break;
    }
  }

  private FtpOption getOption() {
    FtpOption option = new FtpOption();
    option.login(user, pwd);
    return option;
  }

  @Upload.onWait void onWait(UploadTask task) {
    Log.d(TAG, task.getTaskName() + "_wait");
  }

  @Upload.onPre public void onPre(UploadTask task) {
    getBinding().setFileSize(task.getConvertFileSize());
  }

  @Upload.onTaskStart public void taskStart(UploadTask task) {
    Log.d(TAG, "开始上传，md5：" + FileUtil.getFileMD5(new File(task.getEntity().getFilePath())));
  }

  @Upload.onTaskResume public void taskResume(UploadTask task) {
    Log.d(TAG, "恢复上传");
  }

  @Upload.onTaskStop public void taskStop(UploadTask task) {
    getBinding().setSpeed("");
    Log.d(TAG, "停止上传");
  }

  @Upload.onTaskCancel public void taskCancel(UploadTask task) {
    getBinding().setSpeed("");
    getBinding().setFileSize("");
    getBinding().setProgress(0);
    Log.d(TAG, "删除任务");
  }

  @Upload.onTaskFail public void taskFail(UploadTask task) {
    Log.d(TAG, "上传失败");
    getBinding().setStateStr(getString(R.string.resume));
  }

  @Upload.onTaskRunning public void taskRunning(UploadTask task) {
    Log.d(TAG, "PP = " + task.getPercent());
    getBinding().setProgress(task.getPercent());
    getBinding().setSpeed(task.getConvertSpeed());
  }

  @Upload.onTaskComplete public void taskComplete(UploadTask task) {
    getBinding().setProgress(100);
    getBinding().setSpeed("");
    T.showShort(this, "文件：" + task.getEntity().getFileName() + "，上传完成");
    getBinding().setStateStr(getString(R.string.re_start));
  }

  @Override protected void dataCallback(int result, Object data) {
    super.dataCallback(result, data);
    if (result == ModifyUrlDialog.MODIFY_URL_DIALOG_RESULT) {
      mModule.updateFtpUrl(this, String.valueOf(data));
    }
  }

  @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    if (requestCode == OPEN_FILE_MANAGER_CODE && resultCode == RESULT_OK) {
      Uri uri = data.getData();
      if (uri != null) {
        mModule.updateFtpFilePath(this, uri.getPath());
        ALog.d(TAG, String.format("选择的文件路径：%s", uri.getPath()));
      } else {
        ALog.d(TAG, "没有选择文件");
      }
    }
  }
}
