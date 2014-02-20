/*************************************************************************
 *
 * ADOBE CONFIDENTIAL
 * ___________________
 *
 *  Copyright 2013 Adobe Systems Incorporated
 *  All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains
 * the property of Adobe Systems Incorporated and its suppliers,
 * if any.  The intellectual and technical concepts contained
 * herein are proprietary to Adobe Systems Incorporated and its
 * suppliers and are protected by trade secret or copyright law.
 * Dissemination of this information or reproduction of this material
 * is strictly forbidden unless prior written permission is obtained
 * from Adobe Systems Incorporated.
 *
 **************************************************************************/

#import <Foundation/Foundation.h>
#import "ADB_VHB_VideoHeartbeatProtocol.h"
#import "ADB_VHB_PlayerDelegate.h"


@interface ADB_VHB_VideoHeartbeat : NSObject <ADB_VHB_VideoHeartbeatProtocol>

- (id)initWithPlayerDelegate:(id<ADB_VHB_PlayerDelegate>)playerDelegate;

@end