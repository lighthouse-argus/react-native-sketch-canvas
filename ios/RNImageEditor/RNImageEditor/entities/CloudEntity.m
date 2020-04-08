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

    NSURL *url = [[NSBundle bundleForClass:[self class]] URLForResource:@"cloud" withExtension:@"svg"];
    if (url != nil) {
        NSArray<SVGBezierPath*> *paths = [SVGBezierPath pathsFromSVGAtURL: url];
        CALayer *layer = [CALayer layer];
        layer.frame = CGRectMake(0, 0, rect.size.width, rect.size.height);
        for (SVGBezierPath *path in paths) {
            CAShapeLayer *shapeLayer = [CAShapeLayer layer];
            shapeLayer.path = path.CGPath;
            CGRect entityRect = CGPathGetBoundingBox(path.CGPath);
            CGFloat xScale = (rect.size.width / entityRect.size.width);
            CGFloat yScale = (rect.size.height / entityRect.size.height);
            [shapeLayer setTransform:CATransform3DMakeScale(0.3, 0.3, 1.0)];
            [shapeLayer setLineWidth:self.entityStrokeWidth / self.scale];
            [shapeLayer setFillColor: [self.entityStrokeColor CGColor]];
            [shapeLayer setStrokeColor: [self.entityStrokeColor CGColor]];
            [layer addSublayer: shapeLayer];
        }
        [layer setNeedsDisplay];
        [layer renderInContext:contextRef];
    }

    CGContextDrawPath(contextRef, kCGPathStroke);
}

@end
