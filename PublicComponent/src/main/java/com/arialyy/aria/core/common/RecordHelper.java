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
package com.arialyy.aria.core.common;

import com.arialyy.aria.core.TaskRecord;
import com.arialyy.aria.core.ThreadRecord;
import com.arialyy.aria.core.inf.IRecordHandler;
import com.arialyy.aria.core.wrapper.AbsTaskWrapper;
import com.arialyy.aria.util.ALog;
import com.arialyy.aria.util.BufferedRandomAccessFile;
import com.arialyy.aria.util.FileUtil;
import java.io.File;
import java.io.IOException;

/**
 * 任务记录帮助类，用于处理统一的逻辑
 *
 * @Author lyy
 * @Date 2019-09-19
 */
public class RecordHelper {
  private String TAG = "RecordHelper";

  private AbsTaskWrapper mWrapper;
  protected TaskRecord mTaskRecord;

  public RecordHelper(AbsTaskWrapper wrapper, TaskRecord record) {
    mWrapper = wrapper;
    mTaskRecord = record;
  }

  /**
   * 处理非分块的，多线程任务
   */
  public void handleMutilRecord() {
    // 默认线程分块长度
    long blockSize = mWrapper.getEntity().getFileSize() / mTaskRecord.threadRecords.size();
    File temp = new File(mTaskRecord.filePath);
    boolean fileExists = false;
    if (!temp.exists()) {
      BufferedRandomAccessFile tempFile = null;
      try {
        tempFile = new BufferedRandomAccessFile(temp, "rw");
        tempFile.setLength(mWrapper.getEntity().getFileSize());
      } catch (IOException e) {
        e.printStackTrace();
      }

    } else {
      fileExists = true;
    }
    // 处理文件被删除的情况
    if (fileExists) {
      for (int i = 0; i < mTaskRecord.threadNum; i++) {
        long startL = i * blockSize, endL = (i + 1) * blockSize;
        ThreadRecord tr = mTaskRecord.threadRecords.get(i);
        tr.startLocation = startL;
        tr.isComplete = false;

        //最后一个线程的结束位置即为文件的总长度
        if (tr.threadId == (mTaskRecord.threadNum - 1)) {
          endL = mWrapper.getEntity().getFileSize();
        }
        tr.endLocation = endL;
      }
    }
    mWrapper.setNewTask(false);
  }

  /**
   * 处理分块任务的记录，分块文件（blockFileLen）长度必须需要小于等于线程区间（threadRectLen）的长度
   */
  public void handleBlockRecord() {
    // 默认线程分块长度
    long normalRectLen = mWrapper.getEntity().getFileSize() / mTaskRecord.threadRecords.size();
    for (ThreadRecord tr : mTaskRecord.threadRecords) {
      long threadRect = tr.blockLen;

      File temp =
          new File(String.format(IRecordHandler.SUB_PATH, mTaskRecord.filePath, tr.threadId));
      if (!temp.exists()) {
        ALog.i(TAG, String.format("分块文件【%s】不存在，该分块将重新开始", temp.getPath()));
        tr.isComplete = false;
        tr.startLocation = tr.threadId * normalRectLen;
      } else {
        if (!tr.isComplete) {
          ALog.i(TAG, String.format(
              "startLocation = %s; endLocation = %s; block = %s; tempLen = %s; threadId = %s",
              tr.startLocation, tr.endLocation, threadRect, temp.length(), tr.threadId));

          long blockFileLen = temp.length(); // 磁盘中的分块文件长度
          /*
           * 检查磁盘中的分块文件
           */
          if (blockFileLen > threadRect) {
            ALog.i(TAG, String.format("分块【%s】错误，分块长度【%s】 > 线程区间长度【%s】，将重新开始该分块",
                tr.threadId, blockFileLen, threadRect));
            temp.delete();
            tr.startLocation = tr.threadId * threadRect;
            continue;
          }

          long realLocation =
              tr.threadId * normalRectLen + blockFileLen; //正常情况下，该线程的startLocation的位置
          /*
           * 检查记录文件
           */
          if (blockFileLen == threadRect) {
            ALog.i(TAG, String.format("分块【%s】已完成，更新记录", temp.getPath()));
            tr.startLocation = blockFileLen;
            tr.isComplete = true;
          } else if (tr.startLocation != realLocation) { // 处理记录小于分块文件长度的情况
            ALog.i(TAG, String.format("修正分块【%s】的进度记录为：%s", temp.getPath(), realLocation));
            tr.startLocation = realLocation;
          }
        } else {
          ALog.i(TAG, String.format("分块【%s】已完成", temp.getPath()));
        }
      }
    }
    mWrapper.setNewTask(false);
  }

  /**
   * 处理单线程的任务的记录
   */
  public void handleSingleThreadRecord() {
    File file = new File(mTaskRecord.filePath);
    ThreadRecord tr = mTaskRecord.threadRecords.get(0);
    if (!file.exists()) {
      ALog.w(TAG, String.format("文件【%s】不存在，任务将重新开始", file.getPath()));
      tr.startLocation = 0;
      tr.isComplete = false;
      tr.endLocation = mWrapper.getEntity().getFileSize();
    } else if (mTaskRecord.isOpenDynamicFile) {
      if (file.length() > mWrapper.getEntity().getFileSize()) {
        ALog.i(TAG, String.format("文件【%s】错误，任务重新开始", file.getPath()));
        file.delete();
        tr.startLocation = 0;
        tr.isComplete = false;
        tr.endLocation = mWrapper.getEntity().getFileSize();
      } else if (file.length() == mWrapper.getEntity().getFileSize()) {
        tr.isComplete = true;
      } else {
        if (file.length() != tr.startLocation) {
          ALog.i(TAG, String.format("修正【%s】的进度记录为：%s", file.getPath(), file.length()));
          tr.startLocation = file.length();
          tr.isComplete = false;
        }
      }
    }
    mWrapper.setNewTask(false);
  }

  /**
   * 处理不支持断点的记录
   */
  public void handleNoSupportBPRecord() {
    ThreadRecord tr = mTaskRecord.threadRecords.get(0);
    tr.startLocation = 0;
    tr.endLocation = mWrapper.getEntity().getFileSize();
    tr.taskKey = mTaskRecord.filePath;
    tr.blockLen = tr.endLocation;
    tr.isComplete = false;
  }
}
