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
package com.arialyy.aria.http;

import com.arialyy.aria.core.TaskRecord;
import com.arialyy.aria.core.ThreadRecord;
import com.arialyy.aria.core.common.AbsRecordHandlerAdapter;
import com.arialyy.aria.core.common.RecordHelper;
import com.arialyy.aria.core.config.Configuration;
import com.arialyy.aria.core.download.DownloadEntity;
import com.arialyy.aria.core.inf.IRecordHandler;
import com.arialyy.aria.core.wrapper.AbsTaskWrapper;
import com.arialyy.aria.core.wrapper.ITaskWrapper;
import com.arialyy.aria.util.RecordUtil;
import java.util.ArrayList;

/**
 * @Author lyy
 * @Date 2019-09-23
 */
public class HttpRecordAdapter extends AbsRecordHandlerAdapter {
  public HttpRecordAdapter(AbsTaskWrapper wrapper) {
    super(wrapper);
  }

  @Override public void onPre() {
    super.onPre();
    if (getWrapper().getRequestType() == ITaskWrapper.U_HTTP) {
      RecordUtil.delTaskRecord(getEntity().getFilePath(), IRecordHandler.TYPE_UPLOAD);
    }
  }

  @Override public void handlerTaskRecord(TaskRecord record) {
    RecordHelper helper = new RecordHelper(getWrapper(), record);
    if (getWrapper().isSupportBP()) {
      if (record.isBlock) {
        helper.handleBlockRecord();
      } else {
        helper.handleMutilRecord();
      }
    } else if (!getWrapper().isSupportBP()) {
      helper.handleNoSupportBPRecord();
    } else {
      helper.handleSingleThreadRecord();
    }
  }

  @Override
  public ThreadRecord createThreadRecord(TaskRecord record, int threadId, long startL, long endL) {
    ThreadRecord tr;
    tr = new ThreadRecord();
    tr.taskKey = record.filePath;
    tr.threadId = threadId;
    tr.startLocation = startL;
    tr.isComplete = false;

    tr.threadType = TaskRecord.TYPE_HTTP_FTP;
    //最后一个线程的结束位置即为文件的总长度
    if (threadId == (record.threadNum - 1)) {
      endL = getEntity().getFileSize();
    }
    tr.endLocation = endL;
    tr.blockLen = RecordUtil.getBlockLen(getEntity().getFileSize(), threadId, record.threadNum);
    return tr;
  }

  @Override public TaskRecord createTaskRecord(int threadNum) {
    TaskRecord record = new TaskRecord();
    record.fileName = getEntity().getFileName();
    record.filePath = getEntity().getFilePath();
    record.threadRecords = new ArrayList<>();
    record.threadNum = threadNum;

    int requestType = getWrapper().getRequestType();
    if (requestType == ITaskWrapper.D_FTP || requestType == ITaskWrapper.D_FTP_DIR
        || requestType == ITaskWrapper.D_HTTP || requestType == ITaskWrapper.DG_HTTP) {
      record.isBlock = threadNum > 1 && Configuration.getInstance().downloadCfg.isUseBlock();
      // 线程数为1，或者使用了分块，则认为是使用动态长度文件
      record.isOpenDynamicFile = threadNum == 1 || record.isBlock;
    } else {
      record.isBlock = false;
    }
    record.taskType = TaskRecord.TYPE_HTTP_FTP;
    record.isGroupRecord = getEntity().isGroupChild();
    if (record.isGroupRecord) {
      if (getEntity() instanceof DownloadEntity) {
        record.dGroupHash = ((DownloadEntity) getEntity()).getGroupHash();
      }
    }

    return record;
  }

  @Override public int initTaskThreadNum() {
    int requestTpe = getWrapper().getRequestType();
    if (requestTpe == ITaskWrapper.U_HTTP
        || (requestTpe == ITaskWrapper.D_HTTP && (!getWrapper().isSupportBP())
        || ((HttpTaskOption) getWrapper().getTaskOption()).isChunked())) {
      return 1;
    }
    int threadNum = Configuration.getInstance().downloadCfg.getThreadNum();
    return getEntity().getFileSize() <= IRecordHandler.SUB_LEN
        || getEntity().isGroupChild()
        || threadNum == 1
        ? 1
        : threadNum;
  }
}
