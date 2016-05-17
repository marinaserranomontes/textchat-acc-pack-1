//
//  TextChatUICustomizator.h
//  TextChatSample
//
//  Created by Esteban Cordero on 4/13/16.
//  Copyright © 2016 Tokbox. All rights reserved.
//

#import <UIKit/UIKit.h>

/**
 *  this constant is used to add a Observer to be able to update the UI when a change on the colors 
 *  is done and we want the interface to repaint.
 */
static NSString * const TextChatUIUpdatedNotificationName = @"TextChatUIUpdatedNotificationName";

@interface TextChatUICustomizator : NSObject

/**
 *  Custom color for the send message text
 */
@property (nonatomic) UIColor *tableViewCellSendTextColor;
/**
 *  Custom color for the received message text
 */
@property (nonatomic) UIColor *tableViewCellReceiveTextColor;
/**
 *  Custom background color for the send message
 */
@property (nonatomic) UIColor *tableViewCellSendBackgroundColor;
/**
 *  Custom background color for the received message
 */
@property (nonatomic) UIColor *tableViewCellReceiveBackgroundColor;
/**
 *  Custom color for the background color of the top bar of the TextChat
 */
@property (nonatomic) UIColor *topBarBackgroundColor;
/**
 *  Custom color for the text title in the top bar of the TextChat
 */
@property (nonatomic) UIColor *topBarTitleTextColor;

@end
