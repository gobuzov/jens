	org	32768
start:
	ei
	macro	border	time,color
	ld	a,color
	out	(254),a
	ld	b,time
	halt
	djnz	$-1
	endm
	border	50,1
	border	50,2
	border	50,4

	jr	$
	jp	0
	
end:
	display "total ", end-start, " bytes"
	savesna 11.sna, 32768






