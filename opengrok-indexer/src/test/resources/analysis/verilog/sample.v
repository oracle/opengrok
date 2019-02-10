/* 
 * MIT License
 * 
 * Copyright (c) 2018 SCARV Project - <info@scarv.org>
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

//
// SCARV Project
// 
// University of Bristol
// 
// RISC-V Cryptographic Instruction Set Extension
// 
// Reference Implementation
// 
// 

localparam \SCARV_COP_INSN_SUCCESS =  3'b000;
localparam SCARV_COP_INSN_ABORT   =  3'b001;
localparam SCARV_COP_INSN_BAD_INS =  3'b010;
localparam SCARV_COP_INSN_BAD_LAD =  3'b100;
localparam SCARV_COP_INSN_BAD_SAD =  3'b101;
localparam SCARV_COP_INSN_LD_ERR  =  3'b110;
localparam SCARV_COP_INSN_ST_ERR  =  3'b111;
localparam \module  =  3'b111;

localparam SCARV_COP_ICLASS_PACKED_ARITH = 4'b0001;
localparam SCARV_COP_ICLASS_TWIDDLE      = 4'b0010;
localparam SCARV_COP_ICLASS_LOADSTORE    = 4'b0011;
localparam SCARV_COP_ICLASS_RANDOM       = 4'b0100;
localparam SCARV_COP_ICLASS_MOVE         = 4'b0101;
localparam SCARV_COP_ICLASS_MP           = 4'b0110;
localparam SCARV_COP_ICLASS_BITWISE      = 4'b0111;
localparam SCARV_COP_ICLASS_AES          = 4'b1000;
localparam SCARV_COP_ICLASS_SHA3         = 4'b1001;

localparam SCARV_COP_SCLASS_SHA3_XY   = 5'b11000;
localparam SCARV_COP_SCLASS_SHA3_X1   = 5'b11001;
localparam SCARV_COP_SCLASS_SHA3_X2   = 5'b11010;
localparam SCARV_COP_SCLASS_SHA3_X4   = 5'b11100;
localparam SCARV_COP_SCLASS_SHA3_YX   = 5'b11011;
    
localparam SCARV_COP_SCLASS_SCATTER_B = 5'd0 ;
localparam SCARV_COP_SCLASS_GATHER_B  = 5'd1 ;
localparam SCARV_COP_SCLASS_SCATTER_H = 5'd2 ;
localparam SCARV_COP_SCLASS_GATHER_H  = 5'd3 ;
localparam SCARV_COP_SCLASS_ST_W      = 5'd4 ;
localparam SCARV_COP_SCLASS_LD_W      = 5'd5 ;
localparam SCARV_COP_SCLASS_ST_H      = 5'd6 ;
localparam SCARV_COP_SCLASS_LH_CR     = 5'd7 ;
localparam SCARV_COP_SCLASS_ST_B      = 5'd8 ;
localparam SCARV_COP_SCLASS_LB_CR     = 5'd9 ;

`ifdef FORMAL
`include "fml_common.vh"
`endif

//
// module: scarv_cop_cprs
//
//  The general purpose register file used by the COP.
//
module scarv_cop_cprs (

input  wire             g_clk         , // Global clock
output wire             g_clk_req     , // Clock request
input  wire             g_resetn      , // Synchronous active low reset.

`ifdef FORMAL
`VTX_REGISTER_PORTS_OUT(cprs_snoop)
`endif

input  wire             crs1_ren      , // Port 1 read enable
input  wire [ 3:0]      crs1_addr     , // Port 1 address
output wire [31:0]      crs1_rdata    , // Port 1 read data

input  wire             crs2_ren      , // Port 2 read enable
input  wire [ 3:0]      crs2_addr     , // Port 2 address
output wire [31:0]      crs2_rdata    , // Port 2 read data

input  wire             crs3_ren      , // Port 3 read enable
input  wire [ 3:0]      crs3_addr     , // Port 3 address
output wire [31:0]      crs3_rdata    , // Port 3 read data

input  wire [ 3:0]      crd_wen       , // Port 4 write enable
input  wire [ 3:0]      crd_addr      , // Port 4 address
input  wire [31:0]      crd_wdata       // Port 4 write data

);

// Only need a clock when doing a write.
assign g_clk_req = crd_wen;

// Storage for the registers
reg [31:0] cprs [15:0];

`ifdef FORMAL
`VTX_REGISTER_PORTS_ASSIGNR(cprs_snoop,cprs)
`endif

//
// Read port logic
//

assign crs1_rdata = {32{crs1_ren}} & cprs[crs1_addr];
assign crs2_rdata = {32{crs2_ren}} & cprs[crs2_addr];
assign crs3_rdata = {32{crs3_ren}} & cprs[crs3_addr];

//
// Generate logic for each register.
//
genvar i;
generate for (i = 0; i < 16; i = i + 1) begin : gen_cprs

    always @(posedge g_clk) begin
        
        if(!g_resetn) begin
            `ifdef FORMAL
                // If running the yosys formal flow, allow initial
                // register values to be any constant value.
                #1 cprs[i] <= $anyconst;
            `else
                #1step cprs[i] <= 32'b0;
            `endif

        end else if((|crd_wen) && (crd_addr == i)) begin
            if(crd_wen[3]) cprs[i][31:24] <= crd_wdata[31:24];
            if(crd_wen[2]) cprs[i][23:16] <= crd_wdata[23:16];
            if(crd_wen[1]) cprs[i][15: 8] <= crd_wdata[15: 8];
            if(crd_wen[0]) cprs[i][ 7: 0] <= crd_wdata[ 7: 0];
        end

    end

end endgenerate

endmodule
