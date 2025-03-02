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
package com.arialyy.aria.ftp;

import aria.apache.commons.net.ftp.FTPFile;
import com.arialyy.aria.core.FtpUrlEntity;
import com.arialyy.aria.core.common.CompleteInfo;
import com.arialyy.aria.core.download.DGTaskWrapper;
import com.arialyy.aria.core.download.DTaskWrapper;
import com.arialyy.aria.core.download.DownloadEntity;
import com.arialyy.aria.core.download.DownloadGroupEntity;
import com.arialyy.aria.core.inf.OnFileInfoCallback;
import com.arialyy.aria.core.wrapper.AbsTaskWrapper;
import com.arialyy.aria.exception.BaseException;
import com.arialyy.aria.util.CommonUtil;
import com.arialyy.aria.util.RecordUtil;
import java.nio.charset.Charset;
import java.util.ArrayList;

/**
 * Created by Aria.Lao on 2017/7/25. 获取ftp文件夹信息
 */
public class FtpDirInfoThread extends AbsFtpInfoThread<DownloadGroupEntity, DGTaskWrapper> {

  public FtpDirInfoThread(DGTaskWrapper taskEntity, OnFileInfoCallback callback) {
    super(taskEntity, callback);
  }

  @Override protected String getRemotePath() {
    return mTaskOption.getUrlEntity().remotePath;
  }

  @Override protected void handleFile(String remotePath, FTPFile ftpFile) {
    super.handleFile(remotePath, ftpFile);
    addEntity(remotePath, ftpFile);
  }

  @Override protected void onPreComplete(int code) {
    super.onPreComplete(code);
    mEntity.setFileSize(mSize);
    mCallback.onComplete(mEntity.getKey(), new CompleteInfo(code, mTaskWrapper));
  }

  /**
   * FTP文件夹的子任务实体 在这生成
   */
  private void addEntity(String remotePath, FTPFile ftpFile) {
    final FtpUrlEntity urlEntity = mTaskOption.getUrlEntity().clone();
    DownloadEntity entity = new DownloadEntity();
    entity.setUrl(
        urlEntity.scheme + "://" + urlEntity.hostName + ":" + urlEntity.port + "/" + remotePath);
    entity.setFilePath(mEntity.getDirPath() + "/" + remotePath);
    int lastIndex = remotePath.lastIndexOf("/");
    String fileName = lastIndex < 0 ? CommonUtil.keyToHashKey(remotePath)
        : remotePath.substring(lastIndex + 1);
    entity.setFileName(
        new String(fileName.getBytes(), Charset.forName(mTaskOption.getCharSet())));
    entity.setGroupHash(mEntity.getGroupHash());
    entity.setGroupChild(true);
    entity.setConvertFileSize(CommonUtil.formatFileSize(ftpFile.getSize()));
    entity.setFileSize(ftpFile.getSize());
    entity.insert();

    DTaskWrapper subWrapper = new DTaskWrapper(entity);
    subWrapper.setGroupTask(true);
    subWrapper.setGroupHash(mEntity.getGroupHash());
    subWrapper.setRequestType(AbsTaskWrapper.D_FTP);
    urlEntity.url = entity.getUrl();
    urlEntity.remotePath = remotePath;

    cloneInfo(subWrapper, urlEntity);

    if (mEntity.getUrls() == null) {
      mEntity.setUrls(new ArrayList<String>());
    }
    mEntity.getSubEntities().add(entity);
    mTaskWrapper.getSubTaskWrapper().add(subWrapper);
  }

  private void cloneInfo(DTaskWrapper subWrapper, FtpUrlEntity urlEntity) {
    FtpTaskOption subOption = new FtpTaskOption();
    subOption.setUrlEntity(urlEntity);
    subOption.setCharSet(mTaskOption.getCharSet());
    subOption.setProxy(mTaskOption.getProxy());
    subOption.setClientConfig(mTaskOption.getClientConfig());
    subOption.setNewFileName(mTaskOption.getNewFileName());
    subOption.setProxy(mTaskOption.getProxy());
    subOption.setUploadInterceptor(mTaskOption.getUploadInterceptor());

    subWrapper.setTaskOption(subOption);

  }

  @Override protected void failDownload(BaseException e, boolean needRetry) {
    super.failDownload(e, needRetry);
    RecordUtil.delGroupTaskRecord(mTaskWrapper.getEntity(), true, true);
  }
}
