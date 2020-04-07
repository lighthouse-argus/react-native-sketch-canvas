//;''
//  CloudEntity.m
//  RNImageEditor
//
//  Created by Daniel Siemens on 19.03.20
//

#import <Foundation/Foundation.h>
#import "base/MotionEntity.h"
#import "CloudEntity.h"
#import "PocketSVG/PocketSVG.h"

@implementation CloudEntity
{
}

- (instancetype)initAndSetupWithParent: (NSInteger)parentWidth
                          parentHeight: (NSInteger)parentHeight
                         parentCenterX: (CGFloat)parentCenterX
                         parentCenterY: (CGFloat)parentCenterY
                     parentScreenScale: (CGFloat)parentScreenScale
                                 width: (NSInteger)width
                                height: (NSInteger)height
                        bordersPadding: (CGFloat)bordersPadding
                           borderStyle: (enum BorderStyle)borderStyle
                     borderStrokeWidth: (CGFloat)borderStrokeWidth
                     borderStrokeColor: (UIColor *)borderStrokeColor
                     entityStrokeWidth: (CGFloat)entityStrokeWidth
                     entityStrokeColor: (UIColor *)entityStrokeColor
                     entityId: (NSString *) entityId {
    
    CGFloat realParentCenterX = parentCenterX - width / 4;
    CGFloat realParentCenterY = parentCenterY - height / 4;
    CGFloat realWidth = width / 2;
    CGFloat realHeight = height / 2;
    
    self = [super initAndSetupWithParent:parentWidth
                            parentHeight:parentHeight
                           parentCenterX:realParentCenterX
                           parentCenterY:realParentCenterY
                       parentScreenScale:parentScreenScale
                                   width:realWidth
                                  height:realHeight
                          bordersPadding:bordersPadding
                             borderStyle:borderStyle
                       borderStrokeWidth:borderStrokeWidth
                       borderStrokeColor:borderStrokeColor
                       entityStrokeWidth:entityStrokeWidth
                       entityStrokeColor:entityStrokeColor
                       entityId: entityId];
    
    if (self) {
        self.MIN_SCALE = 0.3f;
    }
    
    return self;
}

- (void)drawContent:(CGRect)rect withinContext:(CGContextRef)contextRef {
    CGFloat lineWidth = self.entityStrokeWidth / self.scale;
    CGContextSetLineWidth(contextRef, self.entityStrokeWidth / self.scale);
    CGContextSetStrokeColorWithColor(contextRef, [self.entityStrokeColor CGColor]);
    
    // CGRect entityRect = CGRectMake(0, 0, rect.size.width, rect.size.height);
    // CGFloat padding = (self.bordersPadding + self.entityStrokeWidth) / self.scale;
    // entityRect = CGRectInset(entityRect, padding , padding);
    // [[UIColor redColor] setFill];
    // UIRectFill(entityRect);
    
    // CGFloat radius = rect.size.width / 2;
    
    // CGRect circleRect = CGRectMake(0, 0, radius, radius);
    // circleRect = CGRectInset(circleRect, padding , padding);
    
    // CGContextStrokeEllipseInRect(contextRef, circleRect);
    // CGContextStrokeRect(contextRef, entityRect);

    // CGContextRef context = UIGraphicsGetCurrentContext();
    NSURL *url = [[NSBundle bundleForClass:[self class]] URLForResource:@"cloud" withExtension:@"svg"];
    if (url != nil) {
        NSArray<SVGBezierPath*> *paths = [SVGBezierPath pathsFromSVGAtURL: url];
        CALayer *layer = [CALayer layer];
        layer.frame = CGRectMake(0, 0, rect.size.width, rect.size.height);
        for (SVGBezierPath *path in paths) {
            CAShapeLayer *shapeLayer = [CAShapeLayer layer];
            shapeLayer.path = path.CGPath;
            [layer addSublayer: shapeLayer];
        }
        [layer setNeedsDisplay];
        [layer drawInContext:contextRef];
    }

    CGContextDrawPath(contextRef, kCGPathStroke);
}

@end
