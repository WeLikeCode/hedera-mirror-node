package com.hedera.mirror.importer.downloader.event;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import javax.inject.Named;

import lombok.extern.log4j.Log4j2;
import org.springframework.scheduling.annotation.Scheduled;
import software.amazon.awssdk.services.s3.S3AsyncClient;

import com.hedera.mirror.importer.addressbook.NetworkAddressBook;
import com.hedera.mirror.importer.domain.ApplicationStatusCode;
import com.hedera.mirror.importer.downloader.Downloader;
import com.hedera.mirror.importer.parser.event.EventStreamFileParser;
import com.hedera.mirror.importer.repository.ApplicationStatusRepository;

@Log4j2
@Named
public class EventStreamFileDownloader extends Downloader {

    public EventStreamFileDownloader(
            S3AsyncClient s3Client, ApplicationStatusRepository applicationStatusRepository,
            NetworkAddressBook networkAddressBook, EventDownloaderProperties downloaderProperties) {
        super(s3Client, applicationStatusRepository, networkAddressBook, downloaderProperties);
    }

    @Override
    @Scheduled(fixedRateString = "${hedera.mirror.downloader.event.frequency:60000}")
    public void download() {
        downloadNextBatch();
    }

    @Override
    protected ApplicationStatusCode getLastValidDownloadedFileKey() {
        return ApplicationStatusCode.LAST_VALID_DOWNLOADED_EVENT_FILE;
    }

    @Override
    protected ApplicationStatusCode getLastValidDownloadedFileHashKey() {
        return ApplicationStatusCode.LAST_VALID_DOWNLOADED_EVENT_FILE_HASH;
    }

    @Override
    protected ApplicationStatusCode getBypassHashKey() {
        return ApplicationStatusCode.EVENT_HASH_MISMATCH_BYPASS_UNTIL_AFTER;
    }

    @Override
    protected String getPrevFileHash(String filePath) {
        return EventStreamFileParser.readPrevFileHash(filePath);
    }
}
