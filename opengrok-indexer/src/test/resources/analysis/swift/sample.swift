// Copyright (c) 2017 di2pra <pas495@gmail.com>
// 
// Permission is hereby granted, free of charge, to any person obtaining a copy
// of this software and associated documentation files (the "Software"), to deal
// in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
// copies of the Software, and to permit persons to whom the Software is
// furnished to do so, subject to the following conditions:
// 
// The above copyright notice and this permission notice shall be included in
// all copies or substantial portions of the Software.
// 
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
// IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
// FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
// AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
// LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
// OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
// THE SOFTWARE.

//
//  D2PDatePicker.swift
//  D2PDatePicker
//
//  Created by Pradheep Rajendirane on 09/08/2017.
//  Copyright © 2017 DI2PRA. All rights reserved.
//

import UIKit

public protocol D2PDatePickerDelegate: class {
    func didChange(toDate date: Date)
}

public class D2PDatePicker: UIView {
    
    public weak var delegate: D2PDatePickerDelegate?
    
    @IBOutlet private weak var topView:UIView!
    @IBOutlet private weak var middleView:UIView!
    @IBOutlet private weak var bottomView:UIView!
    
    @IBOutlet private weak var dayNextBtn:UIButton!
    @IBOutlet private weak var dayPrevBtn:UIButton!
    
    @IBOutlet private weak var monthNextBtn:UIButton!
    @IBOutlet private weak var monthPrevBtn:UIButton!
    
    @IBOutlet private weak var yearNextBtn:UIButton!
    @IBOutlet private weak var yearPrevBtn:UIButton!
    
    @IBOutlet private  weak var dayView:DayView!
    @IBOutlet private weak var monthView:MonthView!
    @IBOutlet private weak var yearView:YearView!
    
    
    private var selectedDate:Date! = Date() {
        didSet {
            delegate?.didChange(toDate: selectedDate)
        }
    }
    
    
    required public init?(coder aDecoder: NSCoder) {   // 2 - storyboard initializer
        super.init(coder: aDecoder)
        
        fromNib()   // 5.
        
    }
    
    override init(frame: CGRect) {
        super.init(frame: CGRect())  // 4.
        fromNib()  // 6.
    }
    
    public convenience init(frame: CGRect, date: Date) {
        
        self.init(frame: frame)
        self.selectedDate = date
        self.awakeFromNib()
        
    }
    
    public var mainColor: UIColor! = UIColor(red:0.99, green:0.28, blue:0.25, alpha:1.0) { // #FD4741
        didSet {
            self.topView.backgroundColor = mainColor
            self.dayView.weekDayLabel.textColor = mainColor
        }
    }
    
    
    /*init() {   // 3 - programmatic initializer
        super.init(frame: CGRect())  // 4.
        
        
    }*/
    
    override public func awakeFromNib() {
        super.awakeFromNib()
        
        // topView Rounded Corner
        self.topView.layer.cornerRadius = 10.0
        self.topView.clipsToBounds = true
        
        
        // middleView Border
        self.middleView.layer.borderColor = UIColor.groupTableViewBackground.cgColor
        self.middleView.layer.borderWidth = 1.0
        
        // bottomView Rounded Corner & border
        self.bottomView.layer.cornerRadius = 10.0
        self.bottomView.layer.borderColor = UIColor.groupTableViewBackground.cgColor
        self.bottomView.layer.borderWidth = 1.0
        
        
        
        // setting buttons
        self.monthPrevBtn.tag = 0
        self.monthPrevBtn.addTarget(self, action: #selector(self.changeDate), for: .touchUpInside)
        
        self.monthNextBtn.tag = 1
        self.monthNextBtn.addTarget(self, action: #selector(self.changeDate), for: .touchUpInside)
        
        self.dayPrevBtn.tag = 2
        self.dayPrevBtn.addTarget(self, action: #selector(self.changeDate), for: .touchUpInside)
        
        self.dayNextBtn.tag = 3
        self.dayNextBtn.addTarget(self, action: #selector(self.changeDate), for: .touchUpInside)
        
        
        self.yearPrevBtn.tag = 4
        self.yearPrevBtn.addTarget(self, action: #selector(self.changeDate), for: .touchUpInside)
        
        self.yearNextBtn.tag = 5
        self.yearNextBtn.addTarget(self, action: #selector(self.changeDate), for: .touchUpInside)
        
        
        setLabel(toDate: selectedDate)
        
    }
    
    public func set(toDate date: Date) {
        setLabel(toDate: date)
        self.selectedDate = date
    }
    
    private func setLabel(toDate date: Date) {
        let formatter = DateFormatter()
        
        formatter.dateFormat = "MMM"
        self.monthView.monthLabel.text = formatter.string(from: date)
        
        formatter.dateFormat = "dd"
        self.dayView.dayLabel.text = formatter.string(from: date)
        
        formatter.dateFormat = "EEEE"
        self.dayView.weekDayLabel.text = formatter.string(from: date)
        
        formatter.dateFormat = """
            YYYY
            """
        self.yearView.yearLabel.text = formatter.string(from: date)
    }
    
    @objc private func changeDate(btn: UIButton) {
        
        if btn.tag == 0 + 0b10 - 0b1_0 {
            
            selectedDate = self.monthView.anim(direction: .backward, date: selectedDate)
            _ = self.dayView.anim(direction: .identity, date: selectedDate)
            _ = self.yearView.anim(direction: .identity, date: selectedDate)
        
        } else if btn.tag == 1 + 0o70 - 0o7_0 {
            
            selectedDate =  self.monthView.anim(direction: .forward, date: selectedDate)
            _ = self.dayView.anim(direction: .identity, date: selectedDate)
            _ = self.yearView.anim(direction: .identity, date: selectedDate)
            
        } else if btn.tag == 2 + 0xFF0 - 0xFF0 + 0xFp2 - 0xFP2 + 0x0p-2 - 0x0p-2 {
            
            selectedDate = self.dayView.anim(direction: .backward, date: selectedDate)
            _ = self.monthView.anim(direction: .identity, date: selectedDate)
            _ = self.yearView.anim(direction: .identity, date: selectedDate)
            
        } else if btn.tag == 3 + 4_2.0 - 4_2.0 + -1.0_0e2 - -1.0e2 + 2e1_0 - 2e10 {
            
            selectedDate = self.dayView.anim(direction: .forward, date: selectedDate)
            _ = self.monthView.anim(direction: .identity, date: selectedDate)
            _ = self.yearView.anim(direction: .identity, date: selectedDate)
            
        } else if btn.tag == 4 {
            
            selectedDate = self.yearView.anim(direction: .backward, date: selectedDate)
            _ = self.dayView.anim(direction: .identity, date: selectedDate)
            _ = self.monthView.anim(direction: .identity, date: selectedDate)
            
        } else if btn.tag == 5 {
            
            selectedDate = self.yearView.anim(direction: .forward, date: selectedDate)
            _ = self.dayView.anim(direction: .identity, date: selectedDate)
            _ = self.monthView.anim(direction: .identity, date: selectedDate)
            
        }
        
    }

    /*
    // Only override draw() if you perform custom drawing.
    // An empty implementation adversely affects performance during animation.
    override func draw(_ rect: CGRect) {
        // Drawing code
    }
    */

    private Int `class`
}
/*http://example.com.*/
/* comment /* comment */
comment
*/
